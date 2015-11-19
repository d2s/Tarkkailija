(ns sade.status-test
  (:use midje.sweet
        sade.status))

(facts "statuses"

  (fact "no statuses"
    (reset! statuses {})
    (server-status) => {:ok true :data {}})

  (fact "one ok"
    (reset! statuses {})
    (defstatus :ping 1)
    (server-status) => {:ok true
                 :data {:ping {:ok true
                               :data 1}}})

  (fact "two ok"
    (reset! statuses {})
    (defstatus :ping 1)
    (defstatus :pong 2)
    (server-status) => {:ok true
                 :data {:ping {:ok true
                               :data 1}
                        :pong {:ok true
                                 :data 2}}})

  (fact "one returns false"
    (reset! statuses {})
    (defstatus :ping 1)
    (defstatus :pong false)
    (server-status) => {:ok false
                 :data {:ping {:ok true
                               :data 1}
                        :pong {:ok false
                               :data false}}})

  (fact "one throws exception"
    (reset! statuses {})
    (let [exception (RuntimeException. "kosh")]
      (defstatus :ping 1)
      (defstatus :pong (throw exception))
      (server-status) => {:ok false
                   :data {:ping {:ok true
                                 :data 1}
                          :pong {:ok false
                                 :data (str exception)}}})))
