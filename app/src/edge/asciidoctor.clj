;; Copyright © 2016-2018, JUXT LTD.

(ns edge.asciidoctor
  (:require
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [hiccup.core :as h]
   [integrant.core :as ig]
   [yada.yada :as yada]))

(defn- coerce-from-ruby-decorator
  "Attributes are given to Asciidoctorj macro callbacks as instances of
  the org.asciidoctor.internal.RubyAttributesMapDecorator class, which
  doesn't behave as a Clojure map. This function coerces to a Clojure
  map."
  [attrs]
  (into {} attrs))

(defn- document-attributes
  "Return the document's attributes from a Asciidoctorj node."
  [el]
  (.getAttributes (.getDocument el)))

(defn engine []
  (let [engine (org.asciidoctor.Asciidoctor$Factory/create)
        reg (.javaExtensionRegistry engine)]
    engine))

(defn load-doc [ctx engine docname content]
  (assert engine)
  (.load
    engine
    content
    (java.util.HashMap. ; Asciidoctor is in JRuby which takes HashMaps
      {"safe" org.asciidoctor.SafeMode/UNSAFE
       "header_footer" true
       "to_file" false
       "backend" "html5"
       "attributes"
       (java.util.HashMap.
         {"docinfo" "shared"
          "docinfodir" (str (io/file "doc"))
          "docname" docname
          "experimental" true
          "figure-caption" false
          "icons" "font"
          "iconfont-cdn" "/public/css/font-awesome.min.css"
          "icontype" "svg"
          "imagesdir" "/public/img"
          "nofooter" true
          ;;"sectlinks" false
          "stylesdir" (str (io/file "resources/asciidoctor/css"))
          "stylesheet" "juxt.css"
          "toc" "left"
          "webfonts" false
          "xrefstyle" "short"})})))

(defn document-routes [config]
  (let [asciidoc-engine (:edge/asciidoctor config)]
    (assert asciidoc-engine)
    ["/"
     [
      ["" (yada/redirect ::doc-resource {:route-params {:name "index"}})]
      ["doc"
       [
        [#{"" "/"} (yada/redirect ::doc-resource {:route-params {:name "index"}})]
        [["/" :name ".html"]
         (yada/resource
           {:id ::doc-resource
            :methods
            {:get
             {:produces [{:media-type "text/html;q=0.8" :charset "utf-8"}
                         {:media-type "application/json"}]
              :response (fn [ctx]
                          (let [path (str "doc/" (-> ctx :parameters :path :name) ".adoc")]
                            (.convert
                              (load-doc
                                ctx
                                asciidoc-engine
                                (-> ctx :parameters :path :name)
                                (slurp (io/file path)))
                              )))}}})]]]]]))

(defmethod ig/init-key :edge/asciidoctor [_ config]
  (engine))
