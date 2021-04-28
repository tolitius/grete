# 0.1.3

- (!) producers are now created without a topic, so topics can be specified on `send!` ([commit](https://github.com/tolitius/grete/commit/d8735810ee433ab0f001a194f082522808d738a9))

#### starting from `0.1.3`

```clojure
=> (def p (g/producer (get-in config [:kafka :producer])))

;; send a couple of messages to topics: "foos" and "bars"
=> (g/send! p "foos" "{:answer 42}")
=> (g/send! p "bars" "{:answer 42}")

=> (g/close p)
```

#### before `0.1.3`

grete/producer returned a map of `{:producer .. :topic ..}`:

```clojure
=> (def p (g/producer "foos" (get-in config [:kafka :producer])))

=> (g/send! p "{:answer 42}")
=> (g/send! p "{:answer 42}")

=> (g/close p)
```
