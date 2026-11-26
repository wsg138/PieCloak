const mineflayer = require('mineflayer')
for (const username of ['FancyQaA', 'FancyQaB']) {
  const bot = mineflayer.createBot({host:'127.0.0.1', port:Number(process.argv[2]), username, version:'1.21.11', auth:'offline'})
  bot._client.on('packet',(data,meta)=>{
    if (meta.name==='spawn_entity') {
      console.log(JSON.stringify({username,event:'spawn',id:data.entityId,at:Date.now()}))
    }
  })
  bot.once('spawn',()=>console.log(JSON.stringify({username,event:'joined'})))
  bot.on('error',err=>console.log(JSON.stringify({username,event:'error',value:String(err)})))
  bot.on('kicked',reason=>console.log(JSON.stringify({username,event:'kicked',value:String(reason)})))
}
