import request from '@/utils/request';
// 绑定设备
export const bindDevice = (deviceKey) => request.post('/userBindDevice', { deviceKey },
    {
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        }
    }
);