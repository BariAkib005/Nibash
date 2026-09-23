import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useBuilding } from '../lib/building'
import { useToast } from '../lib/toast'
import DataTable, { Badge } from '../components/DataTable'
import type { Column } from '../components/DataTable'
import PageHeader from '../components/PageHeader'
import { Button, Field, Modal, Skeleton } from '../components/ui'
import Icon from '../components/Icon'
import { formatDate, formatDateTime } from '../lib/format'
import type { ApiDocument } from '../types'

const ACCEPT = '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.odt,.ods,.odp,.txt,.csv,.rtf,.png,.jpg,.jpeg,.webp'

/**
 * The document repository (spec §8.10). Every download is recorded in the document's audit trail,
 * and a new upload against an existing document continues its version chain — the older version is
 * kept but retired, so the list shows the current version of each document by default.
 */
export default function Documents() {
  const { user } = useAuth()
  const { currentId } = useBuilding()
  const toast = useToast()
  const queryClient = useQueryClient()
  const canManage = user?.role === 'admin' || user?.role === 'committee'
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [showArchived, setShowArchived] = useState(false)
  const [uploadFor, setUploadFor] = useState<ApiDocument | 'new' | null>(null)
  const [historyOf, setHistoryOf] = useState<ApiDocument | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['documents', currentId, page, search, showArchived],
    queryFn: () =>
      api.documents({ page, building_id: currentId, search: search.trim() || undefined, is_active: showArchived ? undefined : true }),
    enabled: Boolean(currentId),
  })

  const archive = useMutation({
    mutationFn: (doc: ApiDocument) => api.updateDocument(doc.id, { is_active: !doc.is_active }),
    onSuccess: (doc) => {
      toast.success(doc.is_active ? `${doc.title} restored.` : `${doc.title} archived.`)
      queryClient.invalidateQueries({ queryKey: ['documents'] })
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Could not update the document.'),
  })

  /**
   * Opens the tab synchronously inside the click, then points it at the file once the audit row
   * is written — a tab opened after an await would be eaten by the popup blocker.
   */
  const download = async (doc: ApiDocument) => {
    const tab = window.open('', '_blank')
    try {
      const { file_path } = await api.downloadDocument(doc.id)
      const url = `/media/${file_path}`
      if (tab) tab.location.href = url
      else window.location.href = url
      queryClient.invalidateQueries({ queryKey: ['document-audit', doc.id] })
    } catch (error) {
      tab?.close()
      toast.error(error instanceof ApiError ? error.message : 'Could not download the document.')
    }
  }

  const columns: Column<ApiDocument>[] = [
    {
      key: 'title',
      header: 'Document',
      render: (doc) => (
        <div className="flex items-start gap-3">
          <span className="mt-0.5 text-brand-700">
            <Icon name="file" size={18} />
          </span>
          <div className="min-w-0">
            <p className="font-medium text-slate-900">{doc.title}</p>
            <p className="mt-0.5 text-[11px] text-slate-500">
              Version {doc.version} · {doc.uploaded_by_name} · {formatDate(doc.uploaded_at)}
            </p>
          </div>
        </div>
      ),
    },
    {
      key: 'type',
      header: 'Type',
      render: (doc) => <span className="text-[11px] uppercase text-slate-500">{fileKind(doc)}</span>,
    },
    {
      key: 'status',
      header: 'Status',
      render: (doc) => <Badge tone={doc.is_active ? 'green' : 'slate'}>{doc.is_active ? 'Current' : 'Archived'}</Badge>,
    },
    {
      key: 'actions',
      header: '',
      className: 'text-right',
      render: (doc) => (
        <div className="flex flex-wrap justify-end gap-1">
          <Button variant="secondary" className="px-2.5 py-1 text-xs" onClick={() => download(doc)}>
            <Icon name="download" size={14} />
            Download
          </Button>
          <Button variant="ghost" className="px-2 py-1 text-xs" onClick={() => setHistoryOf(doc)}>
            History
          </Button>
          {canManage && doc.is_active && (
            <Button variant="ghost" className="px-2 py-1 text-xs" onClick={() => setUploadFor(doc)}>
              New version
            </Button>
          )}
          {canManage && (
            <Button
              variant="ghost"
              className="px-2 py-1 text-xs"
              disabled={archive.isPending}
              onClick={() => archive.mutate(doc)}
            >
              {doc.is_active ? 'Archive' : 'Restore'}
            </Button>
          )}
        </div>
      ),
    },
  ]

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <PageHeader
        title="Documents"
        subtitle="Bylaws, budgets and minutes in one place. Every download is recorded."
        actions={
          canManage && (
            <Button onClick={() => setUploadFor('new')}>
              <Icon name="upload" size={16} />
              Upload document
            </Button>
          )
        }
      />

      <div className="flex flex-wrap items-end gap-3">
        <div className="w-full max-w-xs">
          <Field
            label="Search"
            placeholder="Title contains…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value)
              setPage(1)
            }}
          />
        </div>
        <label className="flex items-center gap-2 pb-3 text-xs text-slate-600">
          <input
            type="checkbox"
            checked={showArchived}
            onChange={(e) => {
              setShowArchived(e.target.checked)
              setPage(1)
            }}
          />
          Include archived and older versions
        </label>
      </div>

      <DataTable
        columns={columns}
        rows={data?.results ?? []}
        rowKey={(doc) => doc.id}
        loading={isLoading}
        page={page}
        count={data?.count ?? 0}
        onPageChange={setPage}
        empty={{
          icon: 'file',
          title: search ? 'No documents match' : 'No documents yet',
          body: search
            ? 'Try a different word from the title.'
            : 'Upload the bylaws, the annual budget or meeting minutes so every resident can find them.',
          action: canManage && !search ? <Button onClick={() => setUploadFor('new')}>Upload document</Button> : undefined,
        }}
      />

      {uploadFor && (
        <UploadDialog
          buildingId={currentId}
          previous={uploadFor === 'new' ? null : uploadFor}
          onClose={() => setUploadFor(null)}
          onDone={(doc) => {
            toast.success(doc.version > 1 ? `${doc.title} is now version ${doc.version}.` : `${doc.title} uploaded.`)
            queryClient.invalidateQueries({ queryKey: ['documents'] })
            setUploadFor(null)
          }}
        />
      )}
      {historyOf && <HistoryDialog doc={historyOf} onClose={() => setHistoryOf(null)} />}
    </div>
  )
}

function UploadDialog({
  buildingId,
  previous,
  onClose,
  onDone,
}: {
  buildingId: number | undefined
  previous: ApiDocument | null
  onClose: () => void
  onDone: (doc: ApiDocument) => void
}) {
  const toast = useToast()
  const [title, setTitle] = useState(previous?.title ?? '')
  const [file, setFile] = useState<File | null>(null)
  const tooLarge = Boolean(file && file.size > 10 * 1024 * 1024)

  const upload = useMutation({
    mutationFn: () => api.uploadDocument({ building: buildingId!, title: title.trim(), parent: previous?.id }, file!),
    onSuccess: onDone,
    onError: (error) => toast.error(error instanceof ApiError ? error.message : 'Upload failed.'),
  })

  return (
    <Modal
      open
      title={previous ? `New version of ${previous.title}` : 'Upload a document'}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={upload.isPending} disabled={!title.trim() || !file || tooLarge} onClick={() => upload.mutate()}>
            Upload
          </Button>
        </>
      }
    >
      {previous && (
        <p className="text-sm text-slate-600">
          This becomes version {previous.version + 1}. Version {previous.version} is kept in the history.
        </p>
      )}
      <Field label="Title" value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} disabled={Boolean(previous)} />
      <Field
        label="File"
        type="file"
        accept={ACCEPT}
        onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        error={tooLarge ? 'Files must be 10 MB or smaller.' : undefined}
        hint="PDF, Office documents, text or images — up to 10 MB."
      />
    </Modal>
  )
}

function HistoryDialog({ doc, onClose }: { doc: ApiDocument; onClose: () => void }) {
  const { data: versions, isLoading: versionsLoading } = useQuery({
    queryKey: ['document-versions', doc.id],
    queryFn: () => api.documentVersions(doc.id),
  })
  const { data: audit, isLoading: auditLoading } = useQuery({
    queryKey: ['document-audit', doc.id],
    queryFn: () => api.documentAudit(doc.id),
  })

  return (
    <Modal open title={doc.title} onClose={onClose}>
      <section>
        <h3 className="dashboard-section-title">Versions</h3>
        {versionsLoading ? (
          <Skeleton className="mt-3 h-16 w-full" />
        ) : (
          <ol className="mt-3 divide-y divide-slate-100 rounded-md border border-slate-200">
            {(versions ?? []).map((v) => (
              <li key={v.id} className="flex items-center justify-between gap-3 px-3 py-2.5 text-xs">
                <span>
                  <span className="font-medium text-slate-900">Version {v.version}</span>
                  <span className="text-slate-500"> · {v.uploaded_by_name} · {formatDate(v.uploaded_at)}</span>
                </span>
                {v.is_active && <Badge tone="green">Current</Badge>}
              </li>
            ))}
          </ol>
        )}
      </section>

      <section>
        <h3 className="dashboard-section-title">Audit trail</h3>
        <p className="mt-1 text-[11px] text-slate-500">Uploads, edits and downloads of this version, newest first.</p>
        {auditLoading ? (
          <Skeleton className="mt-3 h-24 w-full" />
        ) : audit?.length ? (
          <ul className="mt-3 max-h-64 divide-y divide-slate-100 overflow-y-auto rounded-md border border-slate-200">
            {audit.map((row) => (
              <li key={row.id} className="flex items-center justify-between gap-3 px-3 py-2.5 text-xs">
                <span className="flex items-center gap-2">
                  <Badge tone={row.event_type === 'download' ? 'blue' : 'slate'}>{row.event_type}</Badge>
                  <span className="text-slate-700">{row.user_name}</span>
                </span>
                <span className="text-slate-500">{formatDateTime(row.event_time)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-3 text-sm text-slate-500">No activity recorded yet.</p>
        )}
      </section>
    </Modal>
  )
}

function fileKind(doc: ApiDocument): string {
  const extension = doc.file_path.split('.').pop()?.toLowerCase()
  if (extension && extension.length <= 4) return extension
  return doc.mime_type?.split('/').pop() ?? 'file'
}
