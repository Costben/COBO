# 微博 API 参考

> 本项目基于微博公开的 Web / 容器接口实现：`m.weibo.cn`（网页端）、`api.weibo.cn/2/`（容器端）、
> `api.weibo.com/2/`（开放平台）。下面整理实际用到与验证过的 Host、公共参数、接口目录与返回结构。

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

1. **UA 用自己**：`__weibo__11.6.3__android__` 是伪装官方客户端，服务端会按 UA 做能力判定，自建客户端应换成自己的标识。
2. **`cum` 不是普通参数**：它由客户端私有算法对 URL path 计算，服务端按接口校验，纯 HTTP 客户端无法通用复现——这也是本项目改走 `m.weibo.cn` Web API 的原因。
3. **`s` 与账号绑定**：换账号必须重新计算，不能跨账号复用。
4. **Cookie 与 gsid 双轨**：网页登录拿 Cookie，`account/getoauth` 拿 gsid，两者要同时维护。
5. **分片上传可简化**：小文件直接用 `statuses/upload.json` 即可，不必实现 MSS。
6. **卡片协议是最大的工作量**：真正决定信息流观感的是 `card_type` 的渲染覆盖度，而不是列表控件本身。
