import { configureStore } from '@reduxjs/toolkit';
import { useDispatch, useSelector } from 'react-redux';
import { userApi } from './services/rtk/userApi';
import { doctorApi } from './services/rtk/doctorApi';
import { queueApi } from './services/rtk/queueApi';
import { notificationApi } from './services/rtk/notificationApi';

/**
 * Redux store — holds only server-state caches (RTK Query slices).
 * Session state (user / role / token) intentionally stays in AuthContext
 * (see docs/03_ARCHITECTURE.md § Frontend State Management).
 */
export const store = configureStore({
  reducer: {
    [userApi.reducerPath]: userApi.reducer,
    [doctorApi.reducerPath]: doctorApi.reducer,
    [queueApi.reducerPath]: queueApi.reducer,
    [notificationApi.reducerPath]: notificationApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(
      userApi.middleware,
      doctorApi.middleware,
      queueApi.middleware,
      notificationApi.middleware,
    ),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

/** Typed Redux hooks (RTK 2.x `withTypes` style). */
export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();

/**
 * Wipes every RTK Query cache entry. Called by AuthContext.logout() so one
 * user's cached data never leaks into the next session (a real integration
 * hazard when sessions change without a page reload).
 */
export function resetApiState(): void {
  store.dispatch(userApi.util.resetApiState());
  store.dispatch(doctorApi.util.resetApiState());
  store.dispatch(queueApi.util.resetApiState());
  store.dispatch(notificationApi.util.resetApiState());
}
