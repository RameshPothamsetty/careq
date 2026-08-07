import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { doctorApi, useGetDepartmentsQuery } from '../services/rtk/doctorApi';
import { getErrorMessage } from '../services/rtk/baseQuery';

/**
 * Day 10 — RTK Query hook state test.
 *
 * Verifies that a consuming component renders the three hook states
 * (loading / success / error) correctly, with the component wrapped in a
 * REAL Redux Provider (same environment as production). The hook itself is
 * mocked at the module level — deterministic and fast, and the standard
 * approach for hook-state rendering tests.
 *
 * Note: driving the real hook + real fetchBaseQuery in jsdom is blocked by a
 * known undici/jsdom realm mismatch (RTK constructs `new Request(...)` with
 * jsdom's AbortSignal, which undici rejects) — documented in docs/09_TESTING.md.
 * The real `getErrorMessage` normalization IS exercised via the error state.
 */

vi.mock('../services/rtk/doctorApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/rtk/doctorApi')>();
  return {
    ...actual,
    useGetDepartmentsQuery: vi.fn(),
  };
});

const mockedUseGetDepartmentsQuery = vi.mocked(useGetDepartmentsQuery);

type HookState = Partial<ReturnType<typeof useGetDepartmentsQuery>>;

function hookState(overrides: HookState): ReturnType<typeof useGetDepartmentsQuery> {
  return {
    data: undefined,
    error: undefined,
    isError: false,
    isFetching: false,
    isLoading: true,
    isSuccess: false,
    isUninitialized: false,
    refetch: vi.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useGetDepartmentsQuery>;
}

/** Minimal consuming component that renders the hook's three states. */
function DepartmentsConsumer() {
  const { data, isLoading, isError, error } = useGetDepartmentsQuery();
  if (isLoading) {
    return <div>Loading…</div>;
  }
  if (isError) {
    return <div>Error: {getErrorMessage(error)}</div>;
  }
  return (
    <ul>
      {data?.map((d) => (
        <li key={d.id}>{d.name}</li>
      ))}
    </ul>
  );
}

function renderConsumer() {
  const store = configureStore({
    reducer: { [doctorApi.reducerPath]: doctorApi.reducer },
    middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(doctorApi.middleware),
  });
  return render(
    <Provider store={store}>
      <DepartmentsConsumer />
    </Provider>,
  );
}

describe('RTK Query hook states (useGetDepartmentsQuery)', () => {
  beforeEach(() => {
    mockedUseGetDepartmentsQuery.mockReset();
  });

  it('renders the loading placeholder while the request is in flight', () => {
    mockedUseGetDepartmentsQuery.mockReturnValue(hookState({ isLoading: true }));
    renderConsumer();
    expect(screen.getByText('Loading…')).toBeInTheDocument();
  });

  it('renders the fetched data on success', () => {
    mockedUseGetDepartmentsQuery.mockReturnValue(
      hookState({
        isLoading: false,
        data: [
          { id: 1, name: 'Cardiology' },
          { id: 2, name: 'Neurology' },
        ],
      }),
    );
    renderConsumer();
    expect(screen.getByText('Cardiology')).toBeInTheDocument();
    expect(screen.getByText('Neurology')).toBeInTheDocument();
  });

  it('renders the normalized backend error message on failure', () => {
    mockedUseGetDepartmentsQuery.mockReturnValue(
      hookState({
        isLoading: false,
        isError: true,
        error: { status: 500, data: { message: 'Backend exploded', path: '/api/departments' } },
      }),
    );
    renderConsumer();
    // getErrorMessage surfaces data.message (Day 9 shared error shape).
    expect(screen.getByText('Error: Backend exploded')).toBeInTheDocument();
  });

  it('getErrorMessage prefers field-level validation errors from a 400', () => {
    const msg = getErrorMessage({
      status: 400,
      data: {
        message: 'Request validation failed',
        validationErrors: [{ field: 'name', message: 'must not be blank' }],
      },
    });
    expect(msg).toBe('name: must not be blank');
  });
});
