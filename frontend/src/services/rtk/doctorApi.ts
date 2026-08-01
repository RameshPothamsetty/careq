import { createApi } from '@reduxjs/toolkit/query/react';
import { authenticatedBaseQuery } from './baseQuery';
import type {
  DepartmentResponse,
  DepartmentRequest,
  DoctorCatalogResponse,
  DoctorCatalogRequest,
  AvailabilityRequest,
} from '../api';

export interface DoctorFilterParams {
  departmentId?: number;
  specialization?: string;
}

/**
 * doctorApi — doctor-service endpoints: department CRUD, doctor catalog
 * browse/CRUD and the doctor's own availability toggle.
 */
export const doctorApi = createApi({
  reducerPath: 'doctorApi',
  baseQuery: authenticatedBaseQuery,
  tagTypes: ['Department', 'Doctor'],
  endpoints: (builder) => ({
    // ── Departments ────────────────────────────────────────────────
    getDepartments: builder.query<DepartmentResponse[], void>({
      query: () => '/api/departments',
      providesTags: ['Department'],
    }),

    createDepartment: builder.mutation<DepartmentResponse, DepartmentRequest>({
      query: (body) => ({
        url: '/api/departments',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Department'],
    }),

    updateDepartment: builder.mutation<
      DepartmentResponse,
      { id: number; body: DepartmentRequest }
    >({
      query: ({ id, body }) => ({
        url: `/api/departments/${id}`,
        method: 'PUT',
        body,
      }),
      invalidatesTags: ['Department'],
    }),

    deleteDepartment: builder.mutation<void, number>({
      query: (id) => ({
        url: `/api/departments/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Department'],
    }),

    // ── Doctor catalog ─────────────────────────────────────────────
    getDoctors: builder.query<DoctorCatalogResponse[], DoctorFilterParams | void>({
      query: (params) => {
        const query = new URLSearchParams();
        if (params?.departmentId) query.set('departmentId', String(params.departmentId));
        if (params?.specialization) query.set('specialization', params.specialization);
        const qs = query.toString();
        return `/api/doctors${qs ? `?${qs}` : ''}`;
      },
      providesTags: ['Doctor'],
    }),

    getDoctorById: builder.query<DoctorCatalogResponse, number>({
      query: (id) => `/api/doctors/${id}`,
      providesTags: ['Doctor'],
    }),

    createDoctor: builder.mutation<DoctorCatalogResponse, DoctorCatalogRequest>({
      query: (body) => ({
        url: '/api/doctors',
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Doctor'],
    }),

    updateDoctor: builder.mutation<
      DoctorCatalogResponse,
      { id: number; body: DoctorCatalogRequest }
    >({
      query: ({ id, body }) => ({
        url: `/api/doctors/${id}`,
        method: 'PUT',
        body,
      }),
      invalidatesTags: ['Doctor'],
    }),

    deleteDoctor: builder.mutation<void, number>({
      query: (id) => ({
        url: `/api/doctors/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Doctor'],
    }),

    // Doctor's own availability toggle (header-based identity).
    toggleAvailability: builder.mutation<DoctorCatalogResponse, AvailabilityRequest>({
      query: (body) => ({
        url: '/api/doctors/me/availability',
        method: 'PUT',
        body,
      }),
      invalidatesTags: ['Doctor'],
    }),
  }),
});

export const {
  useGetDepartmentsQuery,
  useCreateDepartmentMutation,
  useUpdateDepartmentMutation,
  useDeleteDepartmentMutation,
  useGetDoctorsQuery,
  useGetDoctorByIdQuery,
  useCreateDoctorMutation,
  useUpdateDoctorMutation,
  useDeleteDoctorMutation,
  useToggleAvailabilityMutation,
} = doctorApi;
