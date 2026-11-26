const mineflayer = require('@wp2508/mineflayer')
const readline=require('readline')
const bots=[]
for(const username of ['FancyQaA','FancyQaB']){
  const bot=mineflayer.createBot({host:'127.0.0.1',port:Number(process.argv[2]),username,version:'26.3',auth:'offline'})
  bots.push(bot)
  const emit=(event,value)=>console.log(JSON.stringify({username,event,value}))
  bot._client.on('packet',(data,meta)=>{if(meta.name==='spawn_entity')emit('spawn',data.entityId)})
  bot.once('spawn',()=>emit('joined',true))
  bot.on('error',e=>emit('error',String(e)))
  bot.on('kicked',e=>emit('kicked',String(e)))
}
readline.createInterface({input:process.stdin}).on('line',line=>{
  if(line!=='hidden'&&line!=='revealed')return
  for(const bot of bots){
    const pos=bot.entity.position
    const shulker=bot.blockAt(pos.clone().set(32,80,2))
    const chest=bot.blockAt(pos.clone().set(32,80,-2))
    console.log(JSON.stringify({username:bot.username,event:line,shulker:shulker?.name??null,chest:chest?.name??null}))
  }
})
