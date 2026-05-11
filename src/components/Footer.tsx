export default function Footer({ logo, email }: { logo: string; email: string }) {
  return (
    <footer className="py-8 bg-gray-900 text-gray-400">
      <div className="max-w-6xl mx-auto px-6 flex flex-col md:flex-row items-center justify-between gap-4">
        <span className="text-white font-bold text-lg">{logo}</span>
        <p className="text-sm">© {new Date().getFullYear()} — Built with Next.js</p>
        <a href={`mailto:${email}`} className="text-sm hover:text-white transition-colors">{email}</a>
      </div>
    </footer>
  )
}
