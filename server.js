const express = require('express')
const fs = require('fs')
const path = require('path')

const app = express()
const PORT = process.env.PORT || 3000
const DATA_DIR = path.join(__dirname, 'data')

app.use(express.json())
app.use(express.static(path.join(__dirname, 'public')))

// ── API: read content ──────────────────────────────────────────
app.get('/api/content/:section', (req, res) => {
  const file = path.join(DATA_DIR, `${req.params.section}.json`)
  if (!fs.existsSync(file)) return res.status(404).json({ error: 'Not found' })
  try {
    res.json(JSON.parse(fs.readFileSync(file, 'utf8')))
  } catch {
    res.status(500).json({ error: 'Read error' })
  }
})

// ── API: save content ──────────────────────────────────────────
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

// ── Admin SPA ─────────────────────────────────────────────────
app.get('/admin', (_req, res) => {
  res.sendFile(path.join(__dirname, 'admin', 'index.html'))
})

app.listen(PORT, () => {
  console.log(`\n  Raja J. Nassour website running`)
  console.log(`  Website : http://localhost:${PORT}`)
  console.log(`  Admin   : http://localhost:${PORT}/admin\n`)
})
