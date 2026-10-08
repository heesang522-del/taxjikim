import client from './client';

// 응답: { success, data, meta, error }
// axios 의 res.data 가 틀이고, 실제 데이터는 res.data.data 이다.
export const getCompanies = (page = 1, size = 20) => client.get('/companies', { params: { page, size } });

export const getCompany = (code) => client.get(`/companies/${code}`);