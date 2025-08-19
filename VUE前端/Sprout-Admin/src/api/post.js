import request from '@/utils/request'

// 文章列表
// data: {
//   "title": "",
//   "authorId": "",
//   "startTime": "2020-11-19T09:16:19.000+00:00",
//   "endTime": "",
//   "tagIds": [],
//   "status": 1,
//   "pageNo": 1,
//   "pageSize": 10
//   "status": 1 // 1已发布 0审核中 -1未审核 -2未通过
// }
// 返回值{
//     "code": 200,
//     "data": {
//         "records": [
//             {
//                 "id": "1333447953558765569",
//                 "title": "开心的一天",
//                 "userId": "1349290158897311745",
//                 "username": "admin",
//                 "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/7fdbd99d-9901-47f3-b7e1-f980e49eb278.png",
//                 "alias": "admin",
//                 "comments": 0,
//                 "top": false,
//                 "essence": false,
//                 "collects": 1,
//                 "tags": [
//                     {
//                         "id": "1333447953697177602",
//                         "name": "宠物友好",
//                         "count": 2,
//                         "category": 1
//                     }
//                 ],
//                 "view": 105,
//                 "likes": 1,
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/fd8a9302-70ba-4ea1-80f2-cf9d28b4678e.png",
//                 "createTime": "2020-11-30T16:29:01.000+00:00",
//                 "modifyTime": "2020-12-03T15:56:51.000+00:00"
//             },
//             {
//                 "id": "1915301225939406850",
//                 "title": "欢迎来到「植愈星球」—— 开启你的智能绿植生活新方式",
//                 "userId": "1909093419758678018",
//                 "username": "www",
//                 "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/cb29addc-5c34-4eaa-a2b4-fbe33f5f73ae.png",
//                 "alias": "wwx",
//                 "comments": 0,
//                 "top": false,
//                 "essence": false,
//                 "collects": 0,
//                 "tags": null,
//                 "view": 8,
//                 "likes": 1,
//                 "coverImg": null,
//                 "createTime": "2025-04-24T07:06:11.000+00:00",
//                 "modifyTime": null
//             },
//             {
//                 "id": "1915301313885573122",
//                 "title": "绿植病虫害防治全攻略：守护植物健康生长",
//                 "userId": "1909093419758678018",
//                 "username": "www",
//                 "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/cb29addc-5c34-4eaa-a2b4-fbe33f5f73ae.png",
//                 "alias": "wwx",
//                 "comments": 0,
//                 "top": false,
//                 "essence": false,
//                 "collects": 0,
//                 "tags": [
//                     {
//                         "id": "1332913064463794178",
//                         "name": "病虫害防治",
//                         "count": 5,
//                         "category": 1
//                     }
//                 ],
//                 "view": 1,
//                 "likes": 0,
//                 "coverImg": null,
//                 "createTime": "2025-04-24T07:06:32.000+00:00",
//                 "modifyTime": null
//             },
//             {
//                 "id": "1915301385130020866",
//                 "title": "新手绿植养护入门宝典：轻松开启绿色生活",
//                 "userId": "1909093419758678018",
//                 "username": "www",
//                 "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/cb29addc-5c34-4eaa-a2b4-fbe33f5f73ae.png",
//                 "alias": "wwx",
//                 "comments": 0,
//                 "top": false,
//                 "essence": false,
//                 "collects": 0,
//                 "tags": [
//                     {
//                         "id": "1333676096320106498",
//                         "name": "新手必看",
//                         "count": 6,
//                         "category": 1
//                     }
//                 ],
//                 "view": 3,
//                 "likes": 0,
//                 "coverImg": null,
//                 "createTime": "2025-04-24T07:06:49.000+00:00",
//                 "modifyTime": null
//             },
//             {
//                 "id": "1915301456252833793",
//                 "title": "多肉植物浇水全攻略：精准把控，让萌肉健康生长",
//                 "userId": "1909093419758678018",
//                 "username": "www",
//                 "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/cb29addc-5c34-4eaa-a2b4-fbe33f5f73ae.png",
//                 "alias": "wwx",
//                 "comments": 0,
//                 "top": false,
//                 "essence": false,
//                 "collects": 1,
//                 "tags": [
//                     {
//                         "id": "1909203116406915073",
//                         "name": "多肉王国",
//                         "count": 6,
//                         "category": 1
//                     }
//                 ],
//                 "view": 5,
//                 "likes": 1,
//                 "coverImg": null,
//                 "createTime": "2025-04-24T07:07:06.000+00:00",
//                 "modifyTime": null
//             },
//             {
//                 "id": "1915301522405396482",
//                 "title": "邂逅多肉之美：解锁萌肉养护秘籍",
//                 "userId": "1909093419758678018",
//                 "username": "www",
//                 "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/cb29addc-5c34-4eaa-a2b4-fbe33f5f73ae.png",
//                 "alias": "wwx",
//                 "comments": 5,
//                 "top": false,
//                 "essence": false,
//                 "collects": 0,
//                 "tags": [
//                     {
//                         "id": "1909203116406915073",
//                         "name": "多肉王国",
//                         "count": 6,
//                         "category": 1
//                     }
//                 ],
//                 "view": 4,
//                 "likes": 1,
//                 "coverImg": null,
//                 "createTime": "2025-04-24T07:07:22.000+00:00",
//                 "modifyTime": null
//             },
//             {
//                 "id": "1915301612394188801",
//                 "title": "沙漠来客的温柔养护指南：解锁仙人掌的生命力密码",
//                 "userId": "1909093419758678018",
//                 "username": "www",
//                 "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/cb29addc-5c34-4eaa-a2b4-fbe33f5f73ae.png",
//                 "alias": "wwx",
//                 "comments": 0,
//                 "top": false,
//                 "essence": false,
//                 "collects": 0,
//                 "tags": [
//                     {
//                         "id": "1349631541306732545",
//                         "name": "空气植物",
//                         "count": 4,
//                         "category": 1
//                     }
//                 ],
//                 "view": 3,
//                 "likes": 0,
//                 "coverImg": null,
//                 "createTime": "2025-04-24T07:07:44.000+00:00",
//                 "modifyTime": null
//             },
//             {
//                 "id": "1915301728798707714",
//                 "title": "绿萝：净化空气的绿色卫士",
//                 "userId": "1909093419758678018",
//                 "username": "www",
//                 "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/cb29addc-5c34-4eaa-a2b4-fbe33f5f73ae.png",
//                 "alias": "wwx",
//                 "comments": 3,
//                 "top": false,
//                 "essence": false,
//                 "collects": 0,
//                 "tags": [
//                     {
//                         "id": "1349631541306732545",
//                         "name": "空气植物",
//                         "count": 4,
//                         "category": 1
//                     }
//                 ],
//                 "view": 3,
//                 "likes": 0,
//                 "coverImg": null,
//                 "createTime": "2025-04-24T07:08:11.000+00:00",
//                 "modifyTime": null
//             },
//             {
//                 "id": "1915737197088960514",
//                 "title": "绿植养护入门宝典",
//                 "userId": "1909093419758678018",
//                 "username": "www",
//                 "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/cb29addc-5c34-4eaa-a2b4-fbe33f5f73ae.png",
//                 "alias": "wwx",
//                 "comments": 14,
//                 "top": false,
//                 "essence": false,
//                 "collects": 2,
//                 "tags": [
//                     {
//                         "id": "1332681213568589825",
//                         "name": "光照秘籍",
//                         "count": 16,
//                         "category": 1
//                     },
//                     {
//                         "id": "1332681213631504385",
//                         "name": "施肥攻略",
//                         "count": 10,
//                         "category": 1
//                     },
//                     {
//                         "id": "1332682473218744321",
//                         "name": "修剪整形 ",
//                         "count": 7,
//                         "category": 1
//                     }
//                 ],
//                 "view": 172,
//                 "likes": 3,
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/b9fc1e51-c630-40d0-a67a-304b463558d9.png",
//                 "createTime": "2025-04-25T11:58:35.000+00:00",
//                 "modifyTime": null
//             },
//             {
//                 "id": "1917949572169080834",
//                 "title": "绿植养护全攻略：从新手小白到园艺达人",
//                 "userId": "1915043541686710273",
//                 "username": "zmjkk",
//                 "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/659517f8-7ef2-4af3-b945-24a70e42986a.png",
//                 "alias": "zmjkk",
//                 "comments": 0,
//                 "top": false,
//                 "essence": false,
//                 "collects": 1,
//                 "tags": [
//                     {
//                         "id": "1333676096320106498",
//                         "name": "新手必看",
//                         "count": 6,
//                         "category": 1
//                     }
//                 ],
//                 "view": 3,
//                 "likes": 1,
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/697d25dc-3323-4149-be60-61d856f9b4c8.png",
//                 "createTime": "2025-05-01T14:29:46.000+00:00",
//                 "modifyTime": null
//             }
//         ],
//         "total": 43,
//         "size": 10,
//         "current": 1,
//         "orders": [],
//         "optimizeCountSql": true,
//         "hitCount": false,
//         "countId": null,
//         "maxLimit": null,
//         "searchCount": true,
//         "pages": 5
//     },
//     "message": "操作成功"
// }
export const getPostList = (data) => request.post('/admin/post/list', data)

// 根据id获取文章详情
// 返回值{
//     "code": 200,
//     "data": {
//         "topic": {
//             "id": "1915295557509709826",
//             "title": "多肉植物浇水全攻略：精准把控，让萌肉健康生长",
//             "content": "多肉植物具有较强的耐旱能力，这是因为它们的叶片和茎部能够储存水分。所以浇水频率不宜过高，一般遵循 “见干见湿” 的原则。在春秋季节，这是多肉植物的生长旺季，通常可以每隔 3 - 5 天浇一次水。但具体浇水时间还需根据种植介质的透气性以及环境湿度来调整。如果使用的是透气性好的颗粒土，浇水可以适当频繁一些；而在空气湿度较大的环境中，浇水间隔则要适当延长。\n夏季气温较高，多肉植物会进入休眠或半休眠状态，此时生长缓慢，对水分的需求减少，应控制浇水，大约每隔 7 - 10 天浇一次水即可，并且要避免在中午高温时段浇水，选择在早晨或傍晚较为凉爽的时候进行。同时，要注意浇水的量不宜过多，以免造成根部腐烂。\n冬季气温较低，多肉植物生长也变得缓慢，浇水频率要进一步降低，通常每隔 10 - 15 天浇一次水。如果室内温度较低，甚至可以一个月浇一次水。浇水时同样要选择在温度相对较高的中午进行，避免水温过低对多肉植物造成伤害。\n给多肉植物浇水时，要浇透，让水分充分渗透到土壤中，但要避免积水。可以采用浸盆法或沿着花盆边缘缓慢浇水的方式，尽量不要让水溅到多肉植物的叶片上，尤其是在阳光充足的时候，以免叶片被灼伤。此外，不同品种的多肉植物对水分的需求也略有差异，像一些叶片较薄的多肉可能需要相对多一点的水分，而叶片肥厚的品种则更耐旱，在养护过程中需要根据实际情况灵活调整浇水策略。",
//             "userId": "1909093419758678018",
//             "comments": 0,
//             "collects": 0,
//             "view": 2,
//             "likes": 0,
//             "sectionId": 0,
//             "coverImg": null,
//             "top": false,
//             "essence": false,
//             "createTime": "2025-04-24T06:43:40.000+00:00",
//             "modifyTime": null,
//             "status": 1 // 1已发布 0审核中 -1未审核 -2未通过
//         },
//         "user": {
//             "id": "1909093419758678018",
//             "username": "www",
//             "alias": "wwx",
//             "avatar": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/beaff142-3fb9-4735-9d67-a36b94d4c603.png",
//             "followCount": 2,
//             "followerCount": 0,
//             "topicCount": 9,
//             "columns": null,
//             "commentCount": null
//         },
//         "tags": [
//             {
//                 "id": "1909203116406915073",
//                 "name": "多肉王国",
//                 "count": 5,
//                 "category": 1
//             }
//         ]
//     },
//     "message": "操作成功"
// }
export const getPostDetail = (id) => request.get(`/admin/post?id=${id}`)

// 更新文章
// data: {
//     "id": "1333447953558765569",
//     "title": "养护日记",
//     "content": "<p># 植物种植日志——与仙人球和向日葵的奇妙日常</p><p><br></p><p>## 日期：未知（但绝对是阳光明媚的一天）</p><p><br></p><p>### 🌵 仙人球篇：小小刺头，大大能量</p><p><br></p><p>嘿，小伙伴们，今天咱们家迎来了一个新成员——仙人球！这家伙，圆滚滚的，浑身是刺，看起来就像个缩小版的刺猬，但可比刺猬好伺候多了。它告诉我，它最喜欢的就是干燥、阳光充足的环境，还说什么“给我一点阳光，我就能灿烂”。好吧，看在它这么自信的份上，我决定给它一个大大的窗户边位置，让它尽情享受日光浴。</p><p><br></p><p>**养护小贴士**：</p><p>- **浇水**：记得哦，仙人球可是个节水小能手，别三天两头就给它浇水，那样它会生气的，说不定还会用刺扎你呢！一个月一次，少量即可。</p><p>- **光照**：让它尽可能地晒太阳，但别暴晒过头，咱们得适中，对吧？</p><p>- **施肥**：这家伙对肥料不怎么感冒，一年施个一两次薄肥就足够了。</p><p><br></p><p>### 🌻 向日葵篇：向着阳光，勇敢生长</p><p><br></p><p>接着，我们家还迎来了一位阳光大使——向日葵！这家伙，一见面就笑得合不拢嘴（其实是花盘啦），仿佛在说：“看，我是不是超级正能量？”确实，每次看到它，心情都会莫名好起来。它告诉我，它的梦想就是长得高高的，每天都能追到太阳，真是个有追求的家伙！</p><p><br></p><p>**养护小贴士**：</p><p>- **浇水**：向日葵喜欢湿润，但也不能太溺爱哦，保持土壤微湿就好，别让它淹水了。</p><p>- **光照**：这家伙简直就是太阳的忠实粉丝，必须给它全天候的阳光，不然它会忧郁的。</p><p>- **施肥**：生长期的时候，记得给它加点氮肥和磷肥，让它长得更壮实，花盘更大更饱满。</p><p><br></p><p>### 心得体会：与植物共舞的奇妙感受</p><p><br></p><p>今天和这两位新朋友相处下来，感觉就像是和两个小淘气在玩耍。仙人球虽然外表冷酷，但内心却是个需要关爱的小家伙；向日葵呢，则是个永远充满正能量的乐天派。它们用自己的方式告诉我，每一种生命都有其独特的节奏和需求，只要用心去呵护，就能收获满满的幸福和成就感。</p><p><br></p><p>好啦，今天的种植日志就到这里啦，期待明天和我的小伙伴们——仙人球和向日葵，有更多的趣事发生！咱们下次见！</p>",
//     "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/df237b30-f17e-41aa-b538-8318f496cfdd.png",
//     "tags": [
//         "1915291863758196738"
//     ]
// }
export const updatePost = (data) => request.put('/admin/post/update', data)

// 根据id删除文章
export const deletePost = (ids) => request.delete(`/admin/post/delete?ids=${ids.join(',')}`)