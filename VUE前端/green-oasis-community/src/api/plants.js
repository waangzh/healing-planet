import request from '@/utils/request';
// 分页查询绿植信息
export const getPlants = (params) => {
  return request.get('/plants/list', { params });
};

// 根据id查询绿植信息
export const getPlantById = (id) => {
  return request.get(`/plants?id=${id}`);
};

// 识别植物
export const identifyPlant = (imgUrl) => {
  return request.post(`/plants/identify?imgUrl=${imgUrl}`);
};
