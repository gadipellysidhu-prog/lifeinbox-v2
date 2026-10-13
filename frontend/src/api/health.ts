import { apiClient } from './axios';

export interface HealthResponse {
  status: string;
  service: string;
}

export const fetchHealthStatus = async (): Promise<HealthResponse> => {
  const response = await apiClient.get<HealthResponse>('/api/health');
  return response.data;
};
