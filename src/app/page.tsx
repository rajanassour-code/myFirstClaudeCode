import { readContent } from '@/lib/content'
import Navbar from '@/components/Navbar'
import HeroSection from '@/components/HeroSection'
import AboutSection from '@/components/AboutSection'
import ProjectsSection from '@/components/ProjectsSection'
import ContactSection from '@/components/ContactSection'
import Footer from '@/components/Footer'

export const dynamic = 'force-dynamic'

export default function Home() {
  const nav = readContent<NavContent>('nav')
  const hero = readContent<HeroContent>('hero')
  const about = readContent<AboutContent>('about')
  const projects = readContent<ProjectsContent>('projects')
  const contact = readContent<ContactContent>('contact')

  return (
    <main className="min-h-screen">
      <Navbar content={nav} />
      <HeroSection content={hero} />
      <AboutSection content={about} />
      <ProjectsSection content={projects} />
      <ContactSection content={contact} />
      <Footer logo={nav.logo} email={contact.email} />
    </main>
  )
}

// ─── Content Types ────────────────────────────────────────────────────────────

export interface NavContent {
  logo: string
  links: { label: string; href: string }[]
}

export interface HeroContent {
  name: string
  title: string
  subtitle: string
  ctaPrimary: { label: string; href: string }
  ctaSecondary: { label: string; href: string }
  profileImage: string
  availableForWork: boolean
}

export interface AboutContent {
  heading: string
  bio: string
  skills: string[]
  stats: { label: string; value: string }[]
  resumeUrl: string
}

export interface ProjectsContent {
  heading: string
  subheading: string
  items: {
    id: string
    title: string
    description: string
    tags: string[]
    liveUrl: string
    githubUrl: string
    image: string
    featured: boolean
  }[]
}

export interface ContactContent {
  heading: string
  subheading: string
  email: string
  phone: string
  location: string
  socials: { platform: string; url: string; username: string }[]
  formEnabled: boolean
}
