'use client'
import { useState } from 'react'
import { useContentSave } from '@/hooks/useContentSave'
import SaveBar from '@/components/admin/SaveBar'
import type { HeroContent } from '@/app/page'

const inputCls = 'w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm'

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      {label && <label className="block text-sm font-semibold text-gray-700 mb-1.5">{label}</label>}
      {children}
    </div>
  )
}

export default function HeroEditor({ initialData }: { initialData: HeroContent }) {
  const [data, setData] = useState<HeroContent>(initialData)
  const { save, saving, saved, error } = useContentSave('hero')

  function set<K extends keyof HeroContent>(field: K, value: HeroContent[K]) {
    setData((prev) => ({ ...prev, [field]: value }))
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-gray-900 mb-1">Hero Section</h1>
      <p className="text-sm text-gray-500 mb-8">The main banner visitors see when they land on your site.</p>

      <div className="bg-white rounded-2xl border border-gray-200 p-6 space-y-5">
        <Field label="Your Name">
          <input value={data.name} onChange={(e) => set('name', e.target.value)} className={inputCls} />
        </Field>

        <Field label="Title / Role">
          <input value={data.title} onChange={(e) => set('title', e.target.value)} className={inputCls} />
        </Field>

        <Field label="Subtitle / Description">
          <textarea value={data.subtitle} onChange={(e) => set('subtitle', e.target.value)} rows={3} className={inputCls} />
        </Field>

        <div className="grid grid-cols-2 gap-4">
          <Field label="Primary Button Label">
            <input value={data.ctaPrimary.label} onChange={(e) => set('ctaPrimary', { ...data.ctaPrimary, label: e.target.value })} className={inputCls} />
          </Field>
          <Field label="Primary Button Link">
            <input value={data.ctaPrimary.href} onChange={(e) => set('ctaPrimary', { ...data.ctaPrimary, href: e.target.value })} className={inputCls} />
          </Field>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <Field label="Secondary Button Label">
            <input value={data.ctaSecondary.label} onChange={(e) => set('ctaSecondary', { ...data.ctaSecondary, label: e.target.value })} className={inputCls} />
          </Field>
          <Field label="Secondary Button Link">
            <input value={data.ctaSecondary.href} onChange={(e) => set('ctaSecondary', { ...data.ctaSecondary, href: e.target.value })} className={inputCls} />
          </Field>
        </div>

        <Field label="">
          <label className="flex items-center gap-3 cursor-pointer">
            <input type="checkbox" checked={data.availableForWork}
              onChange={(e) => set('availableForWork', e.target.checked)}
              className="w-4 h-4 accent-indigo-600" />
            <span className="text-sm font-medium text-gray-700">Show &quot;Available for work&quot; badge</span>
          </label>
        </Field>
      </div>

      <SaveBar saving={saving} saved={saved} error={error} onSave={() => save(data)} />
    </div>
  )
}
