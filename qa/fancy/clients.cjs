// Test-only offline protocol clients, loopback only.
const mineflayer = require('mineflayer')
const names = ['FancyQaA', 'FancyQaB']
for (const username of names) {
  const bot = mineflayer.createBot({
    host: '127.0.0.1', port: Number(process.argv[2]), username,
    version: '1.21.11', auth: 'offline'
  })
  const emit = (event, value) => console.log(JSON.stringify({ username, event, value }))
  bot._client.on('packet', (data, meta) => {
    if (meta.name === 'spawn_entity') emit('spawn', data.entityId)
    if (meta.name === 'entity_destroy') {
      for (const id of data.entityIds || []) emit('destroy', id)
    }
  })
  bot.once('spawn', () => emit('joined', true))
  bot.on('error', err => emit('error', String(err)))
  bot.on('kicked', reason => emit('kicked', String(reason)))
}
