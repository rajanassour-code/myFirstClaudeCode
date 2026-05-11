import type { ContactContent } from '@/app/page'

export default function ContactSection({ content }: { content: ContactContent }) {
  return (
    <section id="contact" className="py-24 bg-white">
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-16">
          <h2 className="text-4xl font-bold text-gray-900 mb-4">{content.heading}</h2>
          <p className="text-lg text-gray-500 max-w-xl mx-auto">{content.subheading}</p>
        </div>

        <div className="grid md:grid-cols-2 gap-16 items-start">
          {/* Contact info */}
          <div className="space-y-6">
            <ContactItem icon="✉️" label="Email" value={content.email} href={`mailto:${content.email}`} />
            <ContactItem icon="📞" label="Phone" value={content.phone} href={`tel:${content.phone}`} />
            <ContactItem icon="📍" label="Location" value={content.location} />

            <div className="pt-4 border-t border-gray-100">
              <p className="text-sm font-semibold text-gray-500 mb-4 uppercase tracking-wide">Find me online</p>
              <div className="space-y-3">
                {content.socials.map((social) => (
                  <a key={social.platform} href={social.url} target="_blank" rel="noreferrer"
                    className="flex items-center gap-3 text-gray-700 hover:text-indigo-600 transition-colors group">
                    <span className="text-sm font-semibold w-20 text-gray-400 group-hover:text-indigo-400">{social.platform}</span>
                    <span className="text-sm">{social.username}</span>
                  </a>
                ))}
              </div>
            </div>
          </div>

          {/* Contact form */}
          {content.formEnabled && (
            <form className="space-y-4" onSubmit={(e) => e.preventDefault()}>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">First name</label>
                  <input type="text" placeholder="Rajan" className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Last name</label>
                  <input type="text" placeholder="Assour" className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input type="email" placeholder="you@example.com" className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Message</label>
                <textarea rows={5} placeholder="Tell me about your project..." className="w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm resize-none" />
              </div>
              <button type="submit" className="w-full py-3 bg-indigo-600 text-white font-semibold rounded-xl hover:bg-indigo-700 transition-colors shadow-lg shadow-indigo-200">
                Send Message
              </button>
            </form>
          )}
        </div>
      </div>
    </section>
  )
}

function ContactItem({ icon, label, value, href }: { icon: string; label: string; value: string; href?: string }) {
  const content = (
    <div className="flex items-center gap-4">
      <span className="text-2xl">{icon}</span>
      <div>
        <p className="text-xs text-gray-400 font-medium uppercase tracking-wide">{label}</p>
        <p className="text-gray-800 font-medium">{value}</p>
      </div>
    </div>
  )
  return href
    ? <a href={href} className="hover:text-indigo-600 transition-colors">{content}</a>
    : <div>{content}</div>
}
