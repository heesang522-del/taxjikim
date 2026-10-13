import client from './client'; // 기존에 있는 client.js 임포트

export const uploadTaxPdfApi = async (formData) => {
    // 실제 요청 주소: /api/v1 + /tax/upload-pdf = /api/v1/tax/upload-pdf
    const response = await client.post('/tax/upload-pdf', formData, {
        headers: {
            'Content-Type': 'multipart/form-data',
        },
    });
    return response.data;
};