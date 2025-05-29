mm.log("hello, JS!")
mm.listen(signal.key("w"), (tick) => {
    mm.log(tick)
})