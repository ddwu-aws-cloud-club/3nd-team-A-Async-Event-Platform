import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080', // 백엔드 주소 (일단 local에서 test)
});

// 요청을 보낼 때마다 토큰이 있다면 가로채서 헤더에 넣어줌
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default api;