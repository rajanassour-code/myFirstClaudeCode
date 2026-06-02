const express = require('express')
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const app = express()
const PORT = process.env.PORT || 3000
const DATA_DIR = path.join(__dirname, 'data')

// ── Auth config ───────────────────────────────────────────────
// Change ADMIN_PASSWORD to your desired password, or set it as
// an environment variable: ADMIN_PASSWORD=mypassword node server.js
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'raja2025'
const SESSION_SECRET = process.env.SESSION_SECRET || crypto.randomBytes(32).toString('hex')
const COOKIE_NAME = 'cms_auth'
const COOKIE_MAX_AGE = 8 * 60 * 60 * 1000 // 8 hours

function signToken(value) {
  return value + '.' + crypto.createHmac('sha256', SESSION_SECRET).update(value).digest('hex')
}

function verifyToken(signed) {
  if (!signed) return false
  const [value, sig] = signed.split('.')
  const expected = crypto.createHmac('sha256', SESSION_SECRET).update(value).digest('hex')
  return sig === expected && value === 'authenticated'
}

function isAuthenticated(req) {
  const cookie = req.cookies && req.cookies[COOKIE_NAME]
  return verifyToken(cookie)
}

// ── Middleware ────────────────────────────────────────────────
app.use(express.json())
app.use(express.urlencoded({ extended: false }))

// Simple cookie parser (no extra dependency)
app.use((req, _res, next) => {
  req.cookies = {}
  const header = req.headers.cookie || ''
  header.split(';').forEach(pair => {
    const idx = pair.indexOf('=')
    if (idx < 0) return
    const key = pair.slice(0, idx).trim()
    const val = decodeURIComponent(pair.slice(idx + 1).trim())
    req.cookies[key] = val
  })
  next()
})

app.use(express.static(path.join(__dirname, 'public')))

// ── Auth guard middleware ──────────────────────────────────────
function requireAuth(req, res, next) {
  if (isAuthenticated(req)) return next()
  res.redirect('/admin/login')
}

// ── Login page ────────────────────────────────────────────────
app.get('/admin/login', (req, res) => {
  if (isAuthenticated(req)) return res.redirect('/admin')
  res.sendFile(path.join(__dirname, 'admin', 'login.html'))
})

app.post('/admin/login', (req, res) => {
  const { password } = req.body
  if (password === ADMIN_PASSWORD) {
    const token = signToken('authenticated')
    res.setHeader('Set-Cookie',
      `${COOKIE_NAME}=${encodeURIComponent(token)}; HttpOnly; Path=/; Max-Age=${COOKIE_MAX_AGE / 1000}; SameSite=Strict`)
    res.redirect('/admin')
  } else {
    res.sendFile(path.join(__dirname, 'admin', 'login.html'), {
      headers: { 'X-Login-Error': '1' }
    })
    // Re-send login page with error flag via query param
    res.redirect('/admin/login?error=1')
  }
})

app.get('/admin/logout', (_req, res) => {
  res.setHeader('Set-Cookie', `${COOKIE_NAME}=; HttpOnly; Path=/; Max-Age=0; SameSite=Strict`)
  res.redirect('/admin/login')
})

// ── Admin panel (protected) ───────────────────────────────────
app.get('/admin', requireAuth, (_req, res) => {
  res.sendFile(path.join(__dirname, 'admin', 'index.html'))
})

// ── API: auth check ───────────────────────────────────────────
app.use('/api/content', (req, res, next) => {
  if (!isAuthenticated(req)) return res.status(401).json({ error: 'Unauthorized' })
  next()
})

// ── API: read content ─────────────────────────────────────────
app.get('/api/content/:section', (req, res) => {
  const file = path.join(DATA_DIR, `${req.params.section}.json`)
  if (!fs.existsSync(file)) return res.status(404).json({ error: 'Not found' })
  try {
    res.json(JSON.parse(fs.readFileSync(file, 'utf8')))
  } catch {
    res.status(500).json({ error: 'Read error' })
  }
})

// ── API: save content ─────────────────────────────────────────
app.put('/api/content/:section', (req, res) => {
  const allowed = ['home', 'about', 'experience', 'skills', 'contact']
  if (!allowed.includes(req.params.section))
    return res.status(400).json({ error: 'Unknown section' })
  try {
    const file = path.join(DATA_DIR, `${req.params.section}.json`)
    fs.writeFileSync(file, JSON.stringify(req.body, null, 2), 'utf8')
    res.json({ ok: true })
  } catch {
    res.status(500).json({ error: 'Write error' })
  }
})

app.listen(PORT, () => {
  console.log(`\n  Raja J. Nassour website running`)
  console.log(`  Website : http://localhost:${PORT}`)
  console.log(`  Admin   : http://localhost:${PORT}/admin`)
  console.log(`  Password: ${ADMIN_PASSWORD}\n`)
})
