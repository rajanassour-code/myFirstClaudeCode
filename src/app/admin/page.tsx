import Link from 'next/link'

const sections = [
  {
    title: 'Hero',
    description: 'Name, title, subtitle, CTA buttons, availability status',
    href: '/admin/hero',
    icon: '✨',
    color: 'indigo',
  },
  {
    title: 'About',
    description: 'Bio, skills list, stats, resume link',
    href: '/admin/about',
    icon: '👤',
    color: 'purple',
  },
  {
    title: 'Projects',
    description: 'Add, edit, or remove portfolio projects',
    href: '/admin/projects',
    icon: '🚀',
    color: 'blue',
  },
  {
    title: 'Contact',
    description: 'Email, phone, location, social links',
    href: '/admin/contact',
    icon: '📬',
    color: 'green',
  },
  {
    title: 'Navigation',
    description: 'Logo text and nav menu links',
    href: '/admin/nav',
    icon: '🧭',
    color: 'orange',
  },
]

const colorMap: Record<string, string> = {
  indigo: 'bg-indigo-50 border-indigo-100 hover:border-indigo-300',
  purple: 'bg-purple-50 border-purple-100 hover:border-purple-300',
  blue: 'bg-blue-50 border-blue-100 hover:border-blue-300',
  green: 'bg-green-50 border-green-100 hover:border-green-300',
  orange: 'bg-orange-50 border-orange-100 hover:border-orange-300',
}

export default function AdminDashboard() {
  return (
    <div>
      <div className="mb-10">
        <h1 className="text-3xl font-bold text-gray-900 mb-2">Dashboard</h1>
        <p className="text-gray-500">Select a section to edit your website content.</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
        {sections.map((s) => (
          <Link key={s.href} href={s.href}
            className={`block p-6 rounded-2xl border-2 transition-all ${colorMap[s.color]} group`}>
            <div className="text-3xl mb-4">{s.icon}</div>
            <h2 className="text-lg font-bold text-gray-900 mb-1 group-hover:text-indigo-700 transition-colors">
              {s.title}
            </h2>
            <p className="text-sm text-gray-500">{s.description}</p>
            <div className="mt-4 text-sm font-semibold text-indigo-600 group-hover:underline">Edit →</div>
          </Link>
        ))}
      </div>

      <div className="mt-10 p-5 bg-blue-50 border border-blue-100 rounded-2xl">
        <p className="text-sm text-blue-700">
          <strong>Tip:</strong> Changes you make here are saved instantly to JSON files and reflected on the website.{' '}
          <Link href="/" target="_blank" className="underline">Open the website</Link> in another tab to preview.
        </p>
      </div>
    </div>
  )
}
