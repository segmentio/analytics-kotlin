function fn(n) {
    var x = JSON.stringify(n)
    console.log(x)
    n["newKey"] = "newVal"
    return n
};