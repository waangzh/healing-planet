import request from '@/utils/request'

// 根据绿植id查询植物
// 返回值{
//     "code": 200,
//     "data": {
//         "id": null,
//         "scientificName": "Epipremnum aureum",
//         "commonName": "绿萝",
//         "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/c122426c-d80e-4439-83fa-781b2197c3b2.jpg",
//         "difficulty": 1,
//         "createdAt": "2025-04-21 21:13:45",
//         "lightRequirements": "耐阴",
//         "wateringFrequency": "每周1次",
//         "temperaturePreference": "18-28℃",
//         "humidityPreference": ">40%",
//         "fertilizingTips": "修剪枯黄叶片即可",
//         "detailAdvice": "每月施稀释液肥1次"
//     },
//     "message": "操作成功"
// }
export const getPlantById = (id) => request.get(`/admin/plants?id=${id}`)

// 分页查询绿植
// /admin/plants/list?pageNo=1&pageSize=10&key=
// 返回值{
//     "code": 200,
//     "data": {
//         "records": [
//             {
//                 "id": "1916826221333729281",
//                 "scientificName": "Crassula ovata",
//                 "commonName": "翡翠木",
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/9e135980-4368-409e-9209-a16747f62adf.jpg",
//                 "difficulty": 1,
//                 "createdAt": "2025-04-28 20:05:59",
//                 "lightRequirements": "充足阳光",
//                 "wateringFrequency": "完全干透后浇水",
//                 "temperaturePreference": "18-24°C",
//                 "humidityPreference": "干燥环境",
//                 "fertilizingTips": "生长季每月施仙人掌专用肥",
//                 "detailAdvice": "多肉植物，冬季需减少浇水"
//             },
//             {
//                 "id": "1916826181382983682",
//                 "scientificName": "Maranta leuconeura",
//                 "commonName": "孔雀竹芋",
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/929a9ebd-644f-458b-8823-b0454b6dbbe3.jpg",
//                 "difficulty": 3,
//                 "createdAt": "2025-04-28 20:05:49",
//                 "lightRequirements": "中等散射光",
//                 "wateringFrequency": "保持土壤微湿",
//                 "temperaturePreference": "18-24°C",
//                 "humidityPreference": ">60%",
//                 "fertilizingTips": "生长季每2周施稀释肥",
//                 "detailAdvice": "夜间叶片会竖立闭合"
//             },
//             {
//                 "id": "1916826145706233858",
//                 "scientificName": "Schefflera arboricola",
//                 "commonName": "鹅掌柴",
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/ff6aa60d-af68-4d01-9c7d-ffcaa9ee94e6.jpg",
//                 "difficulty": 2,
//                 "createdAt": "2025-04-28 20:05:41",
//                 "lightRequirements": "明亮间接光",
//                 "wateringFrequency": "表土干后浇水",
//                 "temperaturePreference": "16-27°C",
//                 "humidityPreference": "中等湿度",
//                 "fertilizingTips": "生长季每月施稀释肥",
//                 "detailAdvice": "耐修剪，适合造型"
//             },
//             {
//                 "id": "1916826110432137218",
//                 "scientificName": "Spathiphyllum wallisii",
//                 "commonName": "白掌",
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/37b5d2ee-11cb-4207-a5c7-5b8df5d29639.jpg",
//                 "difficulty": 2,
//                 "createdAt": "2025-04-28 20:05:32",
//                 "lightRequirements": "低至中等光照",
//                 "wateringFrequency": "保持土壤湿润",
//                 "temperaturePreference": "18-26°C",
//                 "humidityPreference": ">50%",
//                 "fertilizingTips": "每6-8周施平衡肥",
//                 "detailAdvice": "开花植物，缺水时叶片会下垂"
//             },
//             {
//                 "id": "1916826013434662914",
//                 "scientificName": "Dracaena marginata",
//                 "commonName": "龙血树",
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/8e5e4960-45ab-4ac5-86e7-c2cbb853635c.jpg",
//                 "difficulty": 2,
//                 "createdAt": "2025-04-28 20:05:09",
//                 "lightRequirements": "明亮间接光",
//                 "wateringFrequency": "每10-14天一次",
//                 "temperaturePreference": "18-24°C",
//                 "humidityPreference": "中等湿度",
//                 "fertilizingTips": "春夏每2个月施缓释肥",
//                 "detailAdvice": "对氟敏感，建议使用过滤水"
//             },
//             {
//                 "id": "1916825976315072513",
//                 "scientificName": "Philodendron scandens",
//                 "commonName": "心叶蔓绿绒",
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/b26efad9-0784-424a-a390-1a6a1e82c7e1.jpg",
//                 "difficulty": 1,
//                 "createdAt": "2025-04-28 20:05:00",
//                 "lightRequirements": "低至明亮散射光",
//                 "wateringFrequency": "每周1次",
//                 "temperaturePreference": "18-28°C",
//                 "humidityPreference": "40-60%",
//                 "fertilizingTips": "春夏每3周施平衡肥",
//                 "detailAdvice": "可垂吊或攀爬生长，定期修剪保持造型"
//             },
//             {
//                 "id": "1916825931779952642",
//                 "scientificName": "Peperomia obtusifolia",
//                 "commonName": "西瓜皮椒草",
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/2e346b43-7286-4534-a6f0-7103f2ed8847.jpg",
//                 "difficulty": 2,
//                 "createdAt": "2025-04-28 20:04:50",
//                 "lightRequirements": "中等散射光",
//                 "wateringFrequency": "表土干透后浇水",
//                 "temperaturePreference": "18-24°C",
//                 "humidityPreference": "中等湿度",
//                 "fertilizingTips": "生长季每月稀释液肥",
//                 "detailAdvice": "叶片肥厚多汁，避免叶面积水"
//             },
//             {
//                 "id": "1916825872678014977",
//                 "scientificName": "Zamioculcas zamiifolia",
//                 "commonName": "铁树",
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/fd97508c-da72-45a8-b910-1df23dd43a19.webp",
//                 "difficulty": 1,
//                 "createdAt": "2025-04-28 20:04:36",
//                 "lightRequirements": "耐阴，适应各种光照",
//                 "wateringFrequency": "每2-3周一次",
//                 "temperaturePreference": "18-26°C",
//                 "humidityPreference": "适应性强",
//                 "fertilizingTips": "春夏每2个月施缓释肥",
//                 "detailAdvice": "块茎储水，宁干勿湿"
//             },
//             {
//                 "id": "1916825767015108609",
//                 "scientificName": "Chlorophytum comosum",
//                 "commonName": "空气凤梨",
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/3cac5fbf-ca16-4d4d-bb00-34b0c6c5f415.webp",
//                 "difficulty": 2,
//                 "createdAt": "2025-04-28 20:04:10",
//                 "lightRequirements": "明亮散射光",
//                 "wateringFrequency": "每周浸泡2-3小时",
//                 "temperaturePreference": "15-30°C",
//                 "humidityPreference": "高湿度环境",
//                 "fertilizingTips": "每月一次水溶性肥料喷雾",
//                 "detailAdvice": "无需土壤，浸泡后需倒置晾干"
//             },
//             {
//                 "id": "1",
//                 "scientificName": "Epipremnum aureum",
//                 "commonName": "绿萝",
//                 "coverImg": "https://smart-plant.oss-cn-hangzhou.aliyuncs.com/c122426c-d80e-4439-83fa-781b2197c3b2.jpg",
//                 "difficulty": 1,
//                 "createdAt": "2025-04-21 21:13:45",
//                 "lightRequirements": "耐阴",
//                 "wateringFrequency": "每周1次",
//                 "temperaturePreference": "18-28℃",
//                 "humidityPreference": ">40%",
//                 "fertilizingTips": "修剪枯黄叶片即可",
//                 "detailAdvice": "每月施稀释液肥1次"
//             }
//         ],
//         "total": 19,
//         "size": 10,
//         "current": 1,
//         "orders": [],
//         "optimizeCountSql": true,
//         "hitCount": false,
//         "countId": null,
//         "maxLimit": null,
//         "searchCount": true,
//         "pages": 2
//     },
//     "message": "操作成功"
// }
export const getPlantsList = (params) => request.get(`/admin/plants/list`, { params })

// 新增绿植
// data: {
//   "scientificName": "Monstera deliciosa",
//   "commonName": "龟背竹22",
//   "coverImg": "/images/plants/monstera.jpg", 需要调用图片上传接口
//   "difficulty": 2,
//   "lightRequirements": "明亮的散射光，避免阳光直射",
//   "wateringFrequency": "每7-10天浇水一次，待土壤表面干燥后再浇",
//   "temperaturePreference": "18-27°C，冬季不低于10°C",
//   "humidityPreference": "喜欢高湿度环境，50%-70%为宜",
//   "fertilizingTips": "生长季节（春夏季）每月施一次稀释的液体肥",
//   "detailAdvice": "定期擦拭叶片以保持光泽，生长过快时可适当修剪"
// }
export const addPlant = (data) => request.post(`/admin/plants/add`, data)

// 更新绿植
// data: {
//   "scientificName": "Monstera deliciosa",
//   "commonName": "龟背竹22",
//   "coverImg": "/images/plants/monstera.jpg",
//   "difficulty": 2,
//   "lightRequirements": "明亮的散射光，避免阳光直射",
//   "wateringFrequency": "每7-10天浇水一次，待土壤表面干燥后再浇",
//   "temperaturePreference": "18-27°C，冬季不低于10°C",
//   "humidityPreference": "喜欢高湿度环境，50%-70%为宜",
//   "fertilizingTips": "生长季节（春夏季）每月施一次稀释的液体肥",
//   "detailAdvice": "定期擦拭叶片以保持光泽，生长过快时可适当修剪"
// }
export const updatePlant = (data) => request.put(`/admin/plants/update`, data)

// 批量删除绿植
export const deletePlants = (ids) => request.delete(`/admin/plants/delete?ids=${ids.join(',')}`)

export const getPlantAliases = (plantId) => request.get(`/admin/plants/${plantId}/aliases`)

export const addPlantAlias = (plantId, data) => request.post(`/admin/plants/${plantId}/aliases`, data)

export const updatePlantAlias = (plantId, aliasId, data) =>
  request.put(`/admin/plants/${plantId}/aliases/${aliasId}`, data)

export const deletePlantAlias = (plantId, aliasId) =>
  request.delete(`/admin/plants/${plantId}/aliases/${aliasId}`)
