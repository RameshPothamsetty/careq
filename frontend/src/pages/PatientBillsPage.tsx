import { useState } from 'react';
import { Receipt, CheckCircle2, CreditCard, Wallet } from 'lucide-react';
import { useGetMyBillsQuery, usePayBillMutation } from '../services/rtk/queueApi';
import { getErrorMessage } from '../services/rtk/baseQuery';
import { useI18n } from '../i18n';
import QueuePageHeader from '../components/QueuePageHeader';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/States';
import { useNotifications } from '../context/NotificationContext';

const PAYMENT_METHODS = ['cash', 'upi', 'card'];

function formatMoney(amount: number): string {
  return `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatDate(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * Patient billing — bills are raised automatically when a consultation
 * completes (doctor's catalog fee). Payment is a SANDBOX: it records the
 * chosen method and marks the bill paid; no external gateway is involved.
 */
export default function PatientBillsPage() {
  const { t } = useI18n();
  const { pushToast } = useNotifications();
  const [payingId, setPayingId] = useState<number | null>(null);
  const {
    data: bills,
    isLoading,
    isError,
    error,
    refetch,
  } = useGetMyBillsQuery();
  const [payBill] = usePayBillMutation();

  const pending = (bills ?? []).filter((b) => b.status === 'PENDING');
  const totalDue = pending.reduce((sum, b) => sum + Number(b.amount), 0);

  async function handlePay(billId: number) {
    setPayingId(billId);
    try {
      const method = PAYMENT_METHODS[Math.floor(Math.random() * PAYMENT_METHODS.length)];
      await payBill({ id: billId, body: { paymentMethod: method } }).unwrap();
      pushToast('Payment received', `Bill settled via ${method} (sandbox).`, 'success');
    } catch (err) {
      pushToast('Payment failed', getErrorMessage(err), 'warning');
    } finally {
      setPayingId(null);
    }
  }

  return (
    <div className="min-h-screen bg-mesh-light px-4 py-6 dark:bg-mesh-dark sm:px-6">
      <div className="mx-auto max-w-4xl space-y-6">
        <QueuePageHeader
          icon="🧾"
          title={t('bills.title')}
          subtitle={t('bills.subtitle')}
          dashboardPath="/patient"
        />

        {/* Summary */}
        {bills && bills.length > 0 && (
          <div className="flex flex-wrap items-center gap-4 rounded-3xl border border-brand-100 bg-gradient-to-br from-brand-600 to-brand-800 p-5 text-white shadow-lift">
            <div className="flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white/15">
                <Wallet className="h-6 w-6" />
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-wider text-brand-100">{t('bills.totalDue')}</p>
                <p className="text-2xl font-extrabold tabular-nums">{formatMoney(totalDue)}</p>
              </div>
            </div>
            <p className="ml-auto text-xs font-medium text-brand-100">
              {pending.length} {t('bills.pendingLabel')} · {bills.length} {t('bills.totalLabel')}
            </p>
          </div>
        )}

        {isLoading ? (
          <LoadingState label={t('bills.loading')} />
        ) : isError ? (
          <ErrorState message={getErrorMessage(error)} onRetry={refetch} />
        ) : !bills || bills.length === 0 ? (
          <EmptyState
            icon={<Receipt className="h-8 w-8 text-brand-400" />}
            title={t('bills.emptyTitle')}
            message={t('bills.emptyMessage')}
          />
        ) : (
          <div className="space-y-3">
            {bills.map((bill) => (
              <div
                key={bill.id}
                className="card flex flex-col gap-4 p-5 sm:flex-row sm:items-center"
              >
                <div className="flex min-w-0 flex-1 items-center gap-4">
                  <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-300">
                    <Receipt className="h-6 w-6" />
                  </div>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-base font-bold text-slate-800 dark:text-slate-100">{bill.doctorName}</h3>
                      {bill.status === 'PAID' ? (
                        <span className="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300">
                          {t('bills.paid')}
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300">
                          {t('bills.pending')}
                        </span>
                      )}
                    </div>
                    <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                      {bill.departmentName} · {formatDate(bill.createdAt)}
                      {bill.paymentMethod ? ` · ${bill.paymentMethod}` : ''}
                    </p>
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-4">
                  <p className={`text-lg font-extrabold tabular-nums ${bill.status === 'PAID' ? 'text-emerald-600 dark:text-emerald-400' : 'text-slate-800 dark:text-slate-100'}`}>
                    {formatMoney(bill.amount)}
                  </p>
                  {bill.status === 'PAID' ? (
                    <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-bold text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300">
                      <CheckCircle2 className="h-3.5 w-3.5" /> {t('bills.paid')}
                    </span>
                  ) : (
                    <button
                      onClick={() => handlePay(bill.id)}
                      disabled={payingId === bill.id}
                      className="inline-flex items-center gap-1.5 rounded-full bg-gradient-to-br from-brand-500 to-brand-800 px-4 py-1.5 text-xs font-bold text-white shadow-lift transition-opacity disabled:opacity-50"
                    >
                      <CreditCard className="h-3.5 w-3.5" />
                      {payingId === bill.id ? t('common.loading') : t('bills.payNow')}
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        <footer className="pt-4 text-center text-xs text-slate-400 dark:text-slate-600">
          CareQ — SmartOPD AI | {t('bills.sandboxNote')}
        </footer>
      </div>
    </div>
  );
}
