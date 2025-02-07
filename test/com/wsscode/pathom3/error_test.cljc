(ns com.wsscode.pathom3.error-test
  (:require
    [clojure.test :refer [deftest is are run-tests testing]]
    [com.wsscode.pathom3.connect.built-in.resolvers :as pbir]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.error :as p.error]
    [com.wsscode.pathom3.interface.eql :as p.eql]
    [matcher-combinators.standalone :as mcs]
    [matcher-combinators.test]))

(defn match-error [msg]
  #(-> % ex-message (= msg)))

(deftest test-attribute-error
  (testing "success"
    (is (= (p.error/attribute-error {:foo "value"} :foo)
           nil)))

  (testing "attribute not requested"
    (is (= (p.error/attribute-error {} :foo)
           {::p.error/cause ::p.error/attribute-not-requested})))

  (testing "unreachable from plan"
    (is (= (let [data (p.eql/process
                        (pci/register
                          {::p.error/lenient-mode? true}
                          (pbir/single-attr-resolver :a :b str))
                        [:b])]
             (p.error/attribute-error data :b))
           {::p.error/cause ::p.error/attribute-unreachable})))

  (testing "direct node error"
    (is (mcs/match?
          {::p.error/cause              ::p.error/node-errors
           ::p.error/node-error-details {1 {::p.error/cause     ::p.error/node-exception
                                            ::p.error/exception {:com.wsscode.pathom3.error/error-message "Error"}}}}
          (let [data (p.eql/process
                       (pci/register
                         {::p.error/lenient-mode? true}
                         (pbir/constantly-fn-resolver :a (fn [_] (throw (ex-info "Error" {})))))
                       [:a])]
            (p.error/attribute-error data :a)))))

  (testing "attribute missing on output"
    (is (= (let [data (p.eql/process
                        (pci/register
                          {::p.error/lenient-mode? true}
                          (pco/resolver 'a
                            {::pco/output [:a]}
                            (fn [_ _] {})))
                        [:a])]
             (p.error/attribute-error data :a))
           {::p.error/cause              ::p.error/node-errors
            ::p.error/node-error-details {1 {::p.error/cause ::p.error/attribute-missing}}}))

    (testing "not a problem if attribute is optional"
      (is (= (let [data (p.eql/process
                          (pci/register
                            {::p.error/lenient-mode? true}
                            (pco/resolver 'a
                              {::pco/output [:a]}
                              (fn [_ _] {})))
                          [(pco/? :a)])]
               (p.error/attribute-error data :a))
             nil))))

  (testing "ancestor error"
    (is (mcs/match?
          {::p.error/cause              ::p.error/node-errors
           ::p.error/node-error-details {1 {::p.error/cause             ::p.error/ancestor-error
                                            ::p.error/error-ancestor-id 2
                                            ::p.error/exception         {:com.wsscode.pathom3.error/error-message "Error"}}}}
          (let [data (p.eql/process
                       (pci/register
                         {::p.error/lenient-mode? true}
                         [(pbir/constantly-fn-resolver :a (fn [_] (throw (ex-info "Error" {}))))
                          (pbir/single-attr-resolver :a :b str)])
                       [:b])]
            (p.error/attribute-error data :b)))))

  (testing "ancestor error missing"
    (is (mcs/match?
          {::p.error/cause              ::p.error/node-errors
           ::p.error/node-error-details {1 {::p.error/cause     ::p.error/node-exception
                                            ::p.error/exception {:com.wsscode.pathom3.error/error-message "Insufficient data calling resolver '-unqualified/a->b--attr-transform. Missing attrs :a"}}}}
          (let [data (p.eql/process
                       (pci/register
                         {::p.error/lenient-mode? true}
                         [(pco/resolver 'a
                            {::pco/output [:a]}
                            (fn [_ _] {}))
                          (pbir/single-attr-resolver :a :b str)])
                       [:b])]
            (p.error/attribute-error data :b)))))

  (testing "multiple errors"
    (is (mcs/match?
          {}
          (let [response (p.eql/process
                           (pci/register
                             {::p.error/lenient-mode? true}
                             [(pco/resolver 'err1
                                {::pco/output [:error-demo]}
                                (fn [_ _] (throw (ex-info "One Error" {}))))
                              (pco/resolver 'err2
                                {::pco/output [:error-demo]}
                                (fn [_ _] (throw (ex-info "Other Error" {}))))])
                           [:error-demo])]
            (p.error/attribute-error response :error-demo))))))
