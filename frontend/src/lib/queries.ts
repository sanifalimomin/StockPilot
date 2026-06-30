import { useQuery } from '@tanstack/react-query';
import { api } from '../api';

export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: () => api.listCategories() });
}

export function useSuppliers() {
  return useQuery({ queryKey: ['suppliers'], queryFn: () => api.listSuppliers() });
}

export function useWarehouses() {
  return useQuery({ queryKey: ['warehouses'], queryFn: () => api.listWarehouses() });
}

export function useProductsAll() {
  return useQuery({ queryKey: ['products', 'all'], queryFn: () => api.listProducts() });
}
