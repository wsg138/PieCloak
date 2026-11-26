const mineflayer = require('@wp2508/mineflayer')
const readline=require('readline')
const bots=[]
for(const username of ['FancyQaA','FancyQaB']){
  const bot=mineflayer.createBot({host:'127.0.0.1',port:Number(process.argv[2]),username,version:'26.3',auth:'offline'})
  bots.push(bot)
  const emit=(event,value)=>console.log(JSON.stringify({username,event,value}))
  bot._client.on('packet',(data,meta)=>{if(meta.name==='spawn_entity')emit('spawn',data.entityId);if(meta.name==='entity_destroy')for(const id of data.entityIds||[])emit('destroy',id)})
  bot.once('spawn',()=>emit('joined',true))
  bot.on('error',e=>emit('error',String(e)))
  bot.on('kicked',e=>emit('kicked',String(e)))
}
