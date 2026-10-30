import { Building2, ShieldAlert } from 'lucide-react'
import { getFormatter, getTranslations } from 'next-intl/server'

import { PageHeader } from '@/components/page-header'
import { SupplierFormDialog } from '@/components/suppliers/supplier-form-dialog'
import { SupplierMenu } from '@/components/suppliers/supplier-menu'
import { VerifyIbanButton } from '@/components/suppliers/verify-iban-button'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty'
import {
  Item,
  ItemActions,
  ItemContent,
  ItemDescription,
  ItemGroup,
  ItemTitle,
} from '@/components/ui/item'
import { apiFetch } from '@/lib/api'
import type { components } from '@/lib/api-types'
import { getCurrentUser } from '@/lib/session'

type Supplier = components['schemas']['SupplierResponse']

const ibanBadge = {
  VERIFIED:
    'border-status-accepted bg-status-accepted text-status-accepted-foreground',
  PENDING:
    'border-status-review bg-status-review text-status-review-foreground',
} as const

// "DE89370400440532013000" → "DE89 3704 0044 0532 0130 00", easier to compare when reading aloud on the phone
const grouped = (iban: string) => iban.replace(/(.{4})(?=.)/g, '$1 ')

export default async function SuppliersPage() {
  const t = await getTranslations('suppliers')
  const format = await getFormatter()
  const [user, suppliers] = await Promise.all([
    getCurrentUser(),
    apiFetch<Supplier[]>('/api/suppliers'),
  ])

  // Same rules as in the API; the API checks them again on every action
  const roles = user?.roles ?? []
  const isAdmin = roles.includes('admin')
  const canVerify = isAdmin || roles.includes('approver')
  const me = (user?.username ?? '').toLowerCase()

  const day = (value?: string) =>
    value ? format.dateTime(new Date(value), { dateStyle: 'medium' }) : '—'
  const pendingCount = suppliers
    .flatMap((s) => s.ibans ?? [])
    .filter((i) => i.status === 'PENDING').length

  return (
    <>
      <PageHeader title={t('title')} subtitle={t('subtitle')} />

      <div className='flex flex-wrap items-center justify-between gap-3'>
        <p className='text-sm text-muted-foreground'>
          {t('count', { count: suppliers.length })}
        </p>
        {isAdmin && <SupplierFormDialog />}
      </div>

      {canVerify && pendingCount > 0 && (
        <Alert className='border-status-review bg-status-review text-status-review-foreground'>
          <ShieldAlert aria-hidden='true' />
          <AlertTitle>{t('pendingAlert', { count: pendingCount })}</AlertTitle>
          <AlertDescription className='text-current'>
            {t('pendingAlertText')}
          </AlertDescription>
        </Alert>
      )}

      {suppliers.length === 0 ? (
        <Empty className='border border-dashed'>
          <EmptyHeader>
            <EmptyMedia variant='icon'>
              <Building2 aria-hidden='true' />
            </EmptyMedia>
            <EmptyTitle>{t('empty')}</EmptyTitle>
            <EmptyDescription>{t('emptyText')}</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <div className='grid gap-4 lg:grid-cols-2'>
          {suppliers.map((supplier) => {
            const ibans = supplier.ibans ?? []
            const blocked = supplier.status === 'BLOCKED'
            return (
              <Card key={supplier.id} className='shadow-none'>
                <CardHeader>
                  <CardTitle className='flex flex-wrap items-center gap-2'>
                    {supplier.name}
                    {blocked && (
                      <Badge variant='destructive'>{t('blocked')}</Badge>
                    )}
                  </CardTitle>
                  <CardDescription className='font-mono'>
                    {supplier.vatId ?? t('noVatId')}
                  </CardDescription>
                  {isAdmin && supplier.id && (
                    <CardAction>
                      <SupplierMenu
                        supplierId={supplier.id}
                        name={supplier.name ?? ''}
                        blocked={blocked}
                      />
                    </CardAction>
                  )}
                </CardHeader>
                <CardContent>
                  {ibans.length === 0 ? (
                    <p className='text-sm text-muted-foreground'>
                      {t('noIbans')}
                    </p>
                  ) : (
                    <ItemGroup className='gap-2'>
                      {ibans.map((entry) => {
                        const status = entry.status ?? 'PENDING'
                        const mine = (entry.addedBy ?? '').toLowerCase() === me
                        const description =
                          status === 'VERIFIED'
                            ? t('verifiedBy', {
                                who: entry.verifiedBy ?? '?',
                                date: day(entry.verifiedAt),
                              })
                            : mine
                              ? t('addedByYou', { date: day(entry.addedAt) })
                              : t('addedBy', {
                                  who: entry.addedBy ?? '?',
                                  date: day(entry.addedAt),
                                })
                        return (
                          <Item key={entry.iban} variant='outline' size='sm'>
                            <ItemContent>
                              <ItemTitle className='font-mono'>
                                {grouped(entry.iban ?? '')}
                              </ItemTitle>
                              <ItemDescription>{description}</ItemDescription>
                            </ItemContent>
                            <ItemActions>
                              <Badge
                                variant='outline'
                                className={ibanBadge[status]}
                              >
                                {t(`ibanStatus.${status}`)}
                              </Badge>
                              {status === 'PENDING' &&
                                canVerify &&
                                !mine &&
                                supplier.id &&
                                entry.iban && (
                                  <VerifyIbanButton
                                    supplierId={supplier.id}
                                    iban={entry.iban}
                                  />
                                )}
                            </ItemActions>
                          </Item>
                        )
                      })}
                    </ItemGroup>
                  )}
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}
    </>
  )
}
