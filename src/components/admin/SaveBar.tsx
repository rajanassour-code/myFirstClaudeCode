export default function SaveBar({
  saving,
  saved,
  error,
  onSave,
}: {
  saving: boolean
  saved: boolean
  error: string | null
  onSave: () => void
}) {
  return (
    <div className="flex items-center gap-4 mt-8 pt-6 border-t border-gray-200">
      <button
        onClick={onSave}
        disabled={saving}
        className="px-6 py-2.5 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700 transition-colors disabled:opacity-60">
        {saving ? 'Saving…' : 'Save Changes'}
      </button>

      {saved && (
        <span className="text-sm text-green-600 font-medium flex items-center gap-1">
          ✓ Saved successfully
        </span>
      )}
      {error && (
        <span className="text-sm text-red-600 font-medium">✕ {error}</span>
      )}
    </div>
  )
}
