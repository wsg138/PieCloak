// Test-only offline protocol clients, loopback only.
const mineflayer = require('mineflayer')
const names = ['FancyQaA', 'FancyQaB']
const bots = []
for (const username of names) {
  const bot = mineflayer.createBot({
    host: '127.0.0.1', port: Number(process.argv[2]), username,
    version: '1.21.11', auth: 'offline'
  })
  bot.physicsEnabled = false
  bots.push(bot)
  const emit = (event, value) => console.log(JSON.stringify({ username, event, value }))
  bot._client.on('packet', (data, meta) => {
    if (meta.name === 'spawn_entity') emit('spawn', data.entityId)
    if (/entity/.test(meta.name) && /move|teleport|position/.test(meta.name) && Number.isInteger(data.entityId)) emit('move', data.entityId)
    if (meta.name === 'entity_destroy') {
      for (const id of data.entityIds || []) emit('destroy', id)
    }
  })
  bot.once('spawn', () => emit('joined', true))
  bot.on('error', err => emit('error', String(err)))
  bot.on('kicked', reason => emit('kicked', String(reason)))
}

const readline = require('readline').createInterface({ input: process.stdin })
readline.on('line', async line => {
  const [command, value] = line.trim().split(' ')
  if (command !== 'interact') return
  const id = Number(value)
  for (const bot of bots) {
    try {
      const entity = bot.entities[id]
      if (!entity) throw new Error('NPC entity missing from client')
      await bot.activateEntity(entity)
      console.log(JSON.stringify({username:bot.username,event:'clicked',value:id}))
    } catch (error) {
      console.log(JSON.stringify({username:bot.username,event:'click_error',value:String(error)}))
    }
  }
})
