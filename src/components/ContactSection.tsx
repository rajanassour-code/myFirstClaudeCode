import type { ContactContent } from '@/app/page'
import ContactForm from '@/components/ContactForm'

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
          {content.formEnabled && <ContactForm />}
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
