import groovy.json.JsonSlurper

def json = new File('D:\\kha\\workspace\\playtouch\\jaxspot\\module\\api\\src\\main\\resources\\billing.json').text
def conf = new JsonSlurper().parseText(json)
println(conf)
println(conf.products.keySet())
