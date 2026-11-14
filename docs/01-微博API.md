# 微博 API 参考

> 本项目基于微博公开的 Web / 容器接口实现：`m.weibo.cn`（网页端）、`api.weibo.cn/2/`（容器端）、
> `api.weibo.com/2/`（开放平台）。下面整理实际用到与验证过的 Host、公共参数、接口目录与返回结构。

## 超话网页接口核验（2026-11-10）

本次在已登录浏览器中核验 `GET https://m.weibo.cn/api/container/getIndex`，只记录结构与公开容器身份。

| 超话 | 服务端真实基础容器 ID | 实测范围 |
| --- | --- | --- |
| 崩坏星穹铁道 | `100808e1f868bf9980f09ab6908787d7eaf0f0` | `ok=1`，名称、简介与帖子，返回分页游标 |
| 原神 | `100808fc439dedbb06ca5fd858848e521b8716` | 首批 18 帖，使用返回游标继续得到 18 帖，首尾帖子不同 |
| 王之财宝 | `1008089297cbe7ca5afc70e9ddfa42631225fc` | `ok=1`，名称、简介与 18 帖，返回分页游标 |

- 基础容器 ID 的默认页签会变化，本次先后观察到 `_-_feed` 和 `_-_recommend`。原生首页与分页均明确请求 `基础ID_-_feed`，使用 `data.pageInfo.since_id`，游标按字符串保留。原神早先首批游标为 `5363750244714792`，第二批返回 `5363738828084997`；名称入口复核时明确请求 feed，返回正式名称“原神”、18 帖和游标 `5363780813066333`。
- 超话信息从 `pageInfo` 读取，统计展示串位于 `desc_more`，实测为 `阅读965.8亿　帖子481.2万　粉丝1276.2万`。`page_view` 实为 `super`，`portrait_sub_text` 实为榜单名，均不能作为阅读 / 粉丝数。帖子在 `cards[].mblog` 或嵌套 `card_group`。异步推荐卡不纳入原生实现。
- `100103type=38&q=...` 的结果可以是普通 `#话题#` 搜索目的地。名称或关键词不能推出超话 ID。搜索卡保留服务端 `scheme`，仅识别到真实 `100808` 容器才进入原生超话。
- 名称入口使用综合搜索 `100103type=1&q=名称`。实测“原神”的目标位于 `cards` 的 236 型卡片 → `card_group` 的 17 型卡片 → `group`，其 `scheme` 含真实超话 ID，`title_sub` / `item_title` 都只写“超话”，`item_desc` 为“1276万旅行者”。解析保留这一候选，再读取其 feed 页面中经身份校验的 `page_title` 补正式名称。唯一精确同名结果直接进入原生超话，其余候选供选择，失败及空结果可转网页搜索。
- 匿名搜索曾返回 HTTP 432。新增应用入口要求现有已登录账号，将 HTTP 432 / JSON `ok=-100` 映射为登录提示；没有人工注销用户以制造过期场景。失败或未支持的链接可从网页入口继续查看。
- 自动验证：累计覆盖 41 项测试，全部通过；最新 27 项覆盖名称搜索、候选解析与列表状态，零失败、错误或跳过。`:app:assembleDebug` 成功。
- 设备验收：用户报告真机验收无问题。没有专门诱导真实登录过期场景，因此过期提示的设备表现未验证。
- COUI 采用作者 ArtPlus 提交 `63bcad6dc055680e2cbc58640abcbf0329b9250d` 中发布的 `1.2.0-d756fe5c-SNAPSHOT` 二进制。该提交下的模块依赖及刷新接口已核对，源码包仍未提供。固定仓库地址和版本见前端构建配置，依赖详情见前端架构文档。
- 赞评转保持禁用，只读内容中的原生投票转为网页入口。

## 1. Host 与 BaseUrl

| 用途 | BaseUrl | 说明 |
| :--- | :--- | :--- |
| 微博容器 API（主） | `https://api.weibo.cn/2/` | 90% 的请求走这里；返回裸 JSON（无 `ok/data` 包装） |
| 微博开放 API | `https://api.weibo.com/2/` | OAuth2 系接口，带 `.json` 后缀 |
| 上传（旧） | `https://api.weibo.com/2/` + `statuses/upload.json` | multipart |
| 上传（MSS） | `http://upload.api.weibo.com/2/mss/upload.json` | 分片上传 |
| 网页登录 | `https://passport.weibo.cn/` | 二维码、找回密码、SSO |
| 安全中心 | `https://security.weibo.com/` | 安全验证 |
| 话题榜 | `https://top.weibo.cn/weibo/user_timeline` | 单独 host |

## 2. 公共请求参数

统一注入到每个请求：

| 参数 | 值 / 来源 | 说明 |
| :--- | :--- | :--- |
| `lang` | `zh_CN` / 系统 locale | |
| `networktype` | `wifi` / `4g` / … | |
| `c` | `android` | 客户端平台 |
| `from` | `10B63950` + `60`（鸿蒙）/`10`（其它） | 微博渠道号 |
| `wm` | `2468_1001` | 微博机型编码 |
| `oldwm` | 按厂商映射（见下表） | 旧版机型编码 |
| `ua` | `MANUFACTURER-MODEL__weibo__11.6.3__android__android<RELEASE>` | **伪装成微博官方 11.6.3** |
| `v_p` | `89` | 版本协议号 |
| `android_id` | `Settings.Secure.ANDROID_ID` | |
| `wb_version` | `5005` | |
| `skin` | `default` | |
| `v_f` | `2` | |
| `gsid` | 登录后下发 | 会话 ID |

`oldwm` 映射：

| 厂商 | oldwm |
| :--- | :--- |
| OnePlus | `14038_0004` |
| Meizu | `9848_0009` |
| OPPO | `9847_0002` |
| vivo | `9856_0004` |
| Smartisan | `14010_0013` |
| ZUK | `20003_0002` |
| Sony | `9982_90002` |
| Xiaomi | `20005_0002` |
| 其它 | `3333_1001` |

## 3. 签名与风控

容器端（`api.weibo.cn/2/`）除公共参数外，还会校验三个客户端私有值：

| 参数 | 作用 | 说明 |
| :--- | :--- | :--- |
| `s` | 用户级签名 | 与 uid 绑定，换账号需重新计算，不能跨账号复用 |
| `i` | 设备指纹 | 与设备/安装态相关 |
| `cum` | URL 级反爬签名 | 输入是请求 URL 的 path 部分，服务端按接口校验 |

请求头同时带：

| 头 | 值 |
| :--- | :--- |
| `User-Agent` | 客户端自报标识（不要伪装官方客户端） |
| `X-Log-Uid` | 当前 uid |
| `X-Sessionid` | 每次请求生成的 UUID |
| `X-Validator` | 服务端下发，按会话缓存 |

登录态有两条轨道：`gsid`（query 参数）与 Cookie（`SUB` / `SUBP`），二者缺一都可能触发风控。

> 网页端（`m.weibo.cn`）走 Cookie 会话，不校验上述三个值，是自建客户端更稳的一条路。

## 4. 接口目录

### 4.1 时间线 / 信息流

| 方法 | 路径 | 备注 |
| :--- | :--- | :--- |
| GET | `statuses/friends/timeline` | 关注流，`since_id`/`max_id`/`count` |
| GET | `statuses/user_timeline` | 用户主页 |
| GET | `statuses/mentions` | @我 |
| GET | `statuses/page_timeline` | 话题/超话 |
| GET | `statuses/unread_friends_timeline` | 未读关注流 |
| GET | `statuses/unread_hot_timeline` | 未读热门 |
| GET | `statuses/hot_repost_timeline` | 热门转发 |
| GET | `statuses/video_mixtimeline` | 视频流 |
| GET | `statuses/repost_timeline` | 转发列表 |
| GET | `statuses/show` / `!/statuses/show_batch` | 详情/批量 |
| GET | `statuses/extend` | 长文/扩展 |
| GET | `statuses/article_show` | 文章 |
| POST | `statuses/destroy` | 删除 |
| POST | `statuses/repost` / `repost.json` | 转发 |
| POST | `statuses/update.json` | 发博（开放 API） |
| POST | `statuses/upload.json` | 带图发博 |
| POST | `statuses/modify_visible` | 改可见性 |
| POST | `statuses/filter_create` | 屏蔽规则 |
| POST | `!/statuses/filter_center/status/update` | 屏蔽中心 |

### 4.2 评论 / 点赞 / 收藏

| 方法 | 路径 |
| :--- | :--- |
| GET | `comments/show`、`comments/hot_timeline`、`comments/build_comments` |
| GET | `comments/mentions`、`comments/to_me`、`comments/by_me` |
| GET | `comments/approval_list` |
| POST | `comments/create`、`comments/reply`、`comments/destroy` |
| POST | `comments/block_and_delete`、`comments/approval_update`、`comments/permission_update` |
| GET | `like/show`、`like/to_me` |
| POST | `like/set_like`、`like/cancel_like`、`like/update`、`like/destroy` |
| GET | `favorites` |
| POST | `favorites/create`、`favorites/destroy` |

### 4.3 用户 / 关系

| 方法 | 路径 |
| :--- | :--- |
| GET | `profile`、`profile/statuses`、`profile/safety` |
| POST | `profile/set/pure/covers` |
| GET | `users/show_batch`、`users/relation` |
| POST | `users/update`、`users/set_top_status`、`users/cancel_top_status` |
| GET | `friendships/friends`、`friendships/dynamic` |
| POST | `friendships/create`、`friendships/destroy`、`friendships/remark_update` |
| GET | `friendships/followers_destroy` |
| POST | `blocks/create`、`blocks/destroy` |
| GET | `suggestions/interest_page` |
| POST | `report/spam` |

### 4.4 私信 / 群聊

| 方法 | 路径 |
| :--- | :--- |
| GET | `direct_messages/conversation`、`direct_messages/user_list` |
| GET | `!/direct_messages/recent_contacts` |
| GET | `direct_messages/public/messages.json` |
| POST | `direct_messages/create`、`direct_messages/destroy_batch` |
| POST | `direct_messages/block_batch`、`direct_messages/unblock_batch` |
| POST | `direct_messages/recall.json` |
| GET | `groupchat/query`、`groupchat/query_multi`、`groupchat/query_join_groups` |
| POST | `groupchat/create`、`groupchat/join`、`groupchat/quit`、`groupchat/kick` |
| POST | `groupchat/update`、`!/groupchat/update_nick`、`!/groupchat/update_state` |
| POST | `groupchat/apply_join`、`!/groupchat/destroy` |
| GET | `groups`、`groups/allgroups`、`groups/allmembers`、`groups/timeline` |
| POST | `groups/create`、`groups/update`、`groups/update_lists`、`groups/members_add`、`groups/members_destroy`、`!/groups/destroy` |

#### 4.4.1 H5 私信（m.weibo.cn，本项目实际使用）

`direct_messages/*` 是移动端旧接口；H5 私信走的是下面这套。**读不需要 `st`，写必须带 `st`**（`api/config` 取，10 分钟过期）。

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `https://m.weibo.cn/message/msglist?page=N` | 会话列表，`data[]` 每项含 `id` / `text` / `created_at` / `unread` / `user` / `scheme` |
| GET | `https://m.weibo.cn/api/chat/list` | 聊天记录 |
| POST | `https://m.weibo.cn/api/chat/send` | 发私信，form：`uid`、`content`、`st` |
| POST | `https://m.weibo.cn/api/chat/upload` | 发图，multipart：`file`、`tuid` → `data.fids` |

`api/chat/list` 参数：

| 参数 | 必填 | 说明 |
| :--- | :--- | :--- |
| `uid` | 是 | 对方 uid；缺失返回 `errno=20002`「Uid参数为空」 |
| `count` | 否 | 一屏条数，默认 20 |
| `max_id` | 否 | 取更早一页，传当前**最旧**一条的 `id` |
| `since_id` | 否 | 取更新消息，传当前**最新**一条的 `id` |
| `is_continuous` | 否 | 配 `since_id` 用，`1` 表示增量拉取 |

响应：`{"ok":1,"data":{"msgs":[…],"users":{"<uid>":{…}},"total_number":0,"following":…,"last_read_mid":…,"title":"成都人社局"}}`

`msgs` **最新在前**（H5 渲染前会 `reverse()`），字段：

| 字段 | 说明 |
| :--- | :--- |
| `id` | 消息 id，分页锚点 |
| `text` | 正文 HTML；emoji 形如 `<span class="url-icon"><img alt="[太开心]" src="…" /></span>` |
| `sender_id` / `recipient_id` | 发送方 / 接收方 uid |
| `created_at` | `Sun Oct 25 17:48:35 +0800 2026`（Twitter 格式） |
| `media_type` | `0` 文本，`1` 图片 |
| `dm_type` | `9` 系统提示（居中灰条，不画气泡） |
| `msg_status` | 发送状态 |
| `attachment` | 图片消息：`original_image.url` / `width` / `height`、`thumbnail.url`（**`thumbnail` 是对象，不是字符串**）、`fid` / `fidstr` / `filename` / `filesize` |

`api/chat/send` 返回 `{"ok":1,"data":{"msgs":[{…新建的那条…}]}}`；业务拒绝用 `ok:0` + `errno`：

| errno | 含义 |
| :--- | :--- |
| `100006` | `st` 失效 / 请求非法 → 刷新 `api/config` 重发 |
| `20000` | 业务拒绝，**真正原因在 `extra`**，`msg` 只是「出错了，未知错误」（例 `extra`：「由于对方的设置，关注后才能私信」） |
| `20002` | 缺 `uid` |
| `20003` | 用户不存在（账号已注销） |

#### 4.4.2 私信发图（两步：先上传拿 `fids`，再发送）

H5 的 `chat.5eca4190.js` 里 `sendMsg` 之前先 `post("api/chat/upload")`，拿到 `fids` 再走发送接口。**上传字段名是 `file`、目标字段是 `tuid`**（群聊用 `tochatid`），写成 `pic` / `uid` 一律 `{"ok":0,"msg":"参数有误"}`。

1. 上传

```bash
curl -s -H "Cookie: $COOKIE" -F "file=@a.jpg" -F "tuid=2397088380" \
     "https://m.weibo.cn/api/chat/upload?st=$ST"
```

```json
{"ok":1,"data":{"fids":5357969622696022,"vfid":5357969212446155,
 "filename":"0dbe…f1.jpg","filesize":"2M",
 "original_image":{"url":"https://attachment.api.weibo.com/2/mss/msget?fid=…&imageType=origin&source=…","width":600,"height":600},
 "thumbnail":{"url":"https://attachment.api.weibo.com/2/mss/msget_thumbnail?fid=…&high=240&width=240&size=111,240&source=…","width":240,"height":240}}}
```

2. 发送（form，注意是 `fids` + `media_type=1`，不是 `pic_id`）

```bash
curl -s -H "Cookie: $COOKIE" -d "uid=2397088380" -d "content=" -d "media_type=1" \
     -d "fids=5357969622696022" -d "st=$ST" "https://m.weibo.cn/api/chat/send"
```

3. 落到 `api/chat/list` 里的样子：`media_type=1`、`dm_type=1`、`text="附件信息"`，`attachment.original_image` / `attachment.thumbnail` 是上面那两个 URL。

实测注意：

- 上传限制 `5242880` 字节（5 MB），超了 H5 提示「文件过大，请使用客户端上传」；
- `fids` 传错（例如用 `statuses/uploadPic` 拿到的 `pic_id`）→ `errno=21101 参数错误`；
- 附件图 `attachment.api.weibo.com` **必须带登录 cookie**，裸请求 403（148 字节错误页），气泡会塌成一条细边；
- `st` 失效同样返回 `100006`，刷新 `api/config` 后重传即可。

实测注意：

- `total_number` 恒为 `0`，不能当消息总数用；
- 没有「标记已读」接口，`msglist` 的 `unread` 会一直是 `1`，已读只能客户端本地记；
- 时间分隔：相邻两条间隔 > 5 分钟才插一条时间，和 H5 一致。

### 4.5 内容容器 / 卡片

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `container` | 容器定义 |
| GET | `container/get_item` | **单卡片**（核心） |
| GET | `cardlist` | 卡片列表 |
| GET | `page`、`page/panel`、`page/get_fragment`、`page/get_toolbar` | 页面骨架 |
| GET | `page/get_search_suggest` | 搜索建议 |
| GET | `infopage` | 信息页 |
| GET | `client/publisher_list` | 发布器配置 |
| GET | `expression/get` | 表情包 |
| GET | `object/get_songlist` | 音乐 |

### 4.6 搜索 / 热门 / 话题

| 方法 | 路径 |
| :--- | :--- |
| GET | `page/get_search_suggest` |
| GET | `statuses/page_timeline` |
| GET | `https://top.weibo.cn/weibo/user_timeline` |
| GET | `place/nearby_pois` |
| GET | `multimedia/multidiscovery`、`!/multimedia/playback/batch_get` |
| GET | `video/vplus`、`video/pay_playlist_videos` |
| GET | `stories/activity_detail` |
| GET | `question/show`、`question/extend`、`question/watch` |
| GET | `messages/menu_show`、`messages/event_create` |

### 4.7 账号 / 设备 / 提醒

| 方法 | 路径 | 备注 |
| :--- | :--- | :--- |
| POST | `account/login` | 账号密码登录 |
| POST | `account/login_sendcode` | 发短信验证码 |
| POST | `account/login_smsverify` | 短信登录 |
| GET | `account/getoauth` | 换取 gsid/token/cookie |
| POST | `account/avatar_upload` | 头像上传 |
| POST | `captcha/get` | 验证码 |
| GET | `device/get`、`device/check` | 设备注册 |
| GET | `remind/unread_count`、`remind/set_count` | 未读数 |
| GET | `!/statuses/get_ssig_url` | 视频/图片签名 URL |

### 4.8 开放 API（`api.weibo.com/2/`）

`comments/mentions.json`、`comments/to_me.json`、`comments/show.json`、`comments/by_me.json`、
`statuses/update.json`、`statuses/upload.json`、`statuses/repost.json`、`statuses/repost_timeline.json`、
`remind/unread_count.json`、`direct_messages/new.json`、`direct_messages/recall.json`、
`direct_messages/user_list.json`、`direct_messages/public/messages.json`。

## 5. 上传协议

### 5.1 图片（multipart）

`POST https://api.weibo.com/2/statuses/upload.json`（`@Multipart`）

```
status   微博正文
pic      RequestBody（文件）
access_token / gsid / s / i / aid  ...
```

### 5.2 分片（MSS）

分片上传流程，字段：

| 字段 | 含义 |
| :--- | :--- |
| `act` | `send` |
| `chunkcount` / `chunkindex` / `chunksize` | 分片总数/序号/大小 |
| `startloc` | 起始偏移 |
| `sectioncheck` | 分片 MD5 |
| `filecheck` | 整文件 MD5 |
| `filetoken` | 服务端返回的上传令牌 |
| `filelength` | 文件总长 |
| `type` | `pic` / `video` / `dm_attachment_pic` / `dm_attachment_video` |
| `file_source` | `9` |
| `file_upload_from` | `710` |
| `mediaprops` | JSON：`{"ori":0,"pri":0,"print_mark":1}` |

视频额外带 `createtype=localfile`、`raw_md5`、`watermark`。
部分场景用 `X-Up-Auth` 头替代 `gsid`。

## 6. 响应与错误

### 7.1 成功

`api.weibo.cn/2/*` **直接返回业务对象**，例如时间线：

```json
{
  "statuses": [...],
  "since_id": "...", "max_id": "...",
  "total_number": 123, "hasvisible": true,
  "previous_cursor": 0, "next_cursor": 0,
  "interval": 60, "uve_blank": 0, "has_unread": 0,
  "ad": [], "advertises": [], "trends": {}
}
```

### 7.2 失败

```json
{"errmsg":"校验参数不存在","errno":-200,"errtype":"DEFAULT_ERROR","isblock":false}
```

错误码处理：

| errno | 行为 |
| :--- | :--- |
| `-200` | 重试一次 |
| `-1005` / `20031` | 触发安全验证（geetest / 短信 / 网页） |
| `20062` | 从 `exception_key` 找本地异常对象抛出 |
| 其它 | 透传为业务异常 |

### 7.3 卡片

`container/get_item` 的 `data` 是卡片树：

```json
{
  "card_type": 9,
  "mblog": { ... },
  "card_group": [ ... ],
  "scheme": "sinaweibo://...",
  "title": "..."
}
```

客户端按 `card_type` 分发，支持 60+ 种（1/3/4/6/7/8/9/10/11/16/17/19/20/22/24/25/26/27/28/30/31/32/35/40/41/42/47/52/57/58/59/61/63/80/86/87/89/93/98/99/101/102/105/112/118/119/120/121/122/123/127/143/145/146/156/162/164/165/170/...）。

## 7. 接入注意事项

1. **两条通道并存**：`m.weibo.cn` Web API（Cookie 会话）和 `api.weibo.cn/2` 容器端（`gsid` + `s`/`i`/`cum`）各自独立。登录页是网页的，就只写 Cookie；短信登录走容器端，拿到的是 `gsid`。两边不能混用凭证。
2. **容器端的数据接口已经不对第三方宿主开放**（2026-11 实测，微博 16.11.1）。
   - 渠道号是 `10GB195010`（Android）/ `10GB195060`（鸿蒙），在 `classes8.dex` 的 `com.sina.weibo.utils.bn` 里以 `O` / `P` / `Q` 三个常量成对出现。换成它之后，匿名请求的 `-105 客户端身份校验失败` 会退化成 `-100 您的身份已失效`，说明渠道号这一层已经放行。
   - 用 `10GB195010` 发码 + 真实验证码登录，`account/login` 会正常返回 `gsid` **和一份新的 `SUB` cookie**。但拿着这对新凭证打 `remind/unread_count` 和 `search/container_discover`，仍然是 `-105`。
   - 逐项消元：删掉 `s` → `-200 校验参数不存在`；`s` 填 `00000000` → `-105`；删掉 `cum` → 仍然 `-105`；把 `cookie` 换成登录响应里新发的那份 → 仍然 `-105`。所以 `-105` 的含义是 **`s` 被判定为错**，与渠道号、cookie、`cum`、`X-Validator` 都无关。
   - 现役 `s` 由 `com.sina.weibo.security.SLib.nativeNewCalculateS(Context, String, int)`（第三个参数传 `3`）生成，与 `libwbutil.so` 那一代是两套实现。`libslib.so` 是 `libsoshell` 加固壳（SONAME 就是 `libsoshell.so`，靠 `libziparchive` 自解包）；把它拷进别的 App 后 `isUnpackSuccess()` 返回 `false`、`getUnPackResult()` 返回 `Not found library`——壳只在它自己的宿主里解包。
   - **结论：第三方宿主拿不到容器端数据，`m.weibo.cn` 的 Web API 是唯一可行通道。** 两侧凭证仍然不能混用：网页登录只写 Cookie，短信登录只拿 `gsid`，且 `gsid` 与签发它的渠道号绑定（换渠道号使用会回 `-100 由于你近期修改过密码…请重新登录`）。
3. **`cum` 由 `libwbutil.so` 计算**：输入是「`/2/` + path + `?` + 全部其它参数的编码串」，顺序不能变。`s` 按 uid 算，`i` 是固定设备号。这一代实现里只有 `cum` 仍被服务端接受，`s` 已经换代，见上一条。
4. **Cookie 要按域名存**：`.weibo.cn`、`.weibo.com`、`passport.weibo.cn` 各一份，同名 cookie 在域名之间不共用。网页端每次响应都可能用 `Set-Cookie` 滚动 `SUB` / `ALF` / `_T_WM`，收到就要落盘，否则手里那份只会慢慢过期。
5. **分片上传可简化**：小文件直接用 `statuses/upload.json` 即可，不必实现 MSS。
6. **卡片协议是最大的工作量**：真正决定信息流观感的是 `card_type` 的渲染覆盖度，而不是列表控件本身。
