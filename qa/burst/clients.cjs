const mineflayer = require('@wp2508/mineflayer')
for (const username of ['FancyQaA','FancyQaB']) {
  const bot = mineflayer.createBot({host:'127.0.0.1',port:Number(process.argv[2]),username,version:'26.3',auth:'offline'})
  bot._client.on('packet',(data,meta)=>{
    if(meta.name==='spawn_entity' && data.entityId>=430000 && data.entityId<430300)
      console.log(JSON.stringify({username,event:'spawn',id:data.entityId}))
  })
  bot.once('spawn',()=>console.log(JSON.stringify({username,event:'joined'})))
  bot.on('error',err=>console.log(JSON.stringify({username,event:'error',value:String(err)})))
  bot.on('kicked',reason=>console.log(JSON.stringify({username,event:'kicked',value:String(reason)})))
}
