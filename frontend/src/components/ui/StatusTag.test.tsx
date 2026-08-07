import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import StatusTag from './StatusTag';

describe('StatusTag', () => {
  it.each([
    ['EMERGENCY', 'Emergency', 'bg-red-100'],
    ['HIGH', 'High', 'bg-orange-100'],
    ['NORMAL', 'Normal', 'bg-emerald-100'],
    ['FOLLOW_UP', 'Follow-up', 'bg-slate-100'],
    ['WAITING', 'In queue', 'bg-sky-100'],
    ['IN_PROGRESS', 'In consultation', 'bg-violet-100'],
    ['COMPLETED', 'Completed', 'bg-emerald-100'],
    ['CANCELLED', 'Cancelled', 'bg-slate-100'],
    ['ONLINE', 'Online', 'bg-emerald-100'],
    ['OFFLINE', 'Offline', 'bg-slate-100'],
  ] as const)('%s → renders "%s" with the %s color', (status, label, colorClass) => {
    const { container } = render(<StatusTag status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(container.querySelector('span')?.className).toContain(colorClass);
  });

  it('shows the colored dot by default and hides it when showDot=false', () => {
    const { container } = render(<StatusTag status="HIGH" />);
    expect(container.querySelector('.bg-orange-500')).toBeInTheDocument();

    const { container: noDot } = render(<StatusTag status="HIGH" showDot={false} />);
    expect(noDot.querySelector('.bg-orange-500')).not.toBeInTheDocument();
  });

  it('falls back to the raw status string for unknown values', () => {
    render(<StatusTag status="UNKNOWN_STATUS" />);
    expect(screen.getByText('UNKNOWN_STATUS')).toBeInTheDocument();
  });
});
