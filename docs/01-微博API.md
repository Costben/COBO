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
