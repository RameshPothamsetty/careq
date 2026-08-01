import { createApi } from '@reduxjs/toolkit/query/react';
import { authenticatedBaseQuery } from './baseQuery';
import type { UserProfileResponse, UpdateProfilePayload } from '../api';

/**
 * userApi — user-service profile endpoints.
 * GET/PUT /api/users/me and the FormData profile-picture upload.
 */
export const userApi = createApi({
  reducerPath: 'userApi',
  baseQuery: authenticatedBaseQuery,
  tagTypes: ['Profile'],
  endpoints: (builder) => ({
    getProfile: builder.query<UserProfileResponse, void>({
      query: () => '/api/users/me',
      providesTags: ['Profile'],
    }),

    updateProfile: builder.mutation<UserProfileResponse, UpdateProfilePayload>({
      query: (payload) => ({
        url: '/api/users/me',
        method: 'PUT',
        body: payload,
      }),
      invalidatesTags: ['Profile'],
    }),

    // FormData upload — fetchBaseQuery skips the JSON content-type for
    // FormData bodies and lets the browser set the multipart boundary.
    uploadProfilePicture: builder.mutation<UserProfileResponse, File>({
      query: (file) => {
        const formData = new FormData();
        formData.append('file', file);
        return {
          url: '/api/users/me/profile-picture',
          method: 'POST',
          body: formData,
        };
      },
      invalidatesTags: ['Profile'],
    }),
  }),
});

export const {
  useGetProfileQuery,
  useUpdateProfileMutation,
  useUploadProfilePictureMutation,
} = userApi;
