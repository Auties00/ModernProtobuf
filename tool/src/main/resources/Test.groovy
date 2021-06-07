def test = [1:"String",2:"Message"]
test.collectEntries {""}.collect().join(", ")
