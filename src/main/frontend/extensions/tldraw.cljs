(ns frontend.extensions.tldraw
  (:require ["/tldraw-logseq" :as TldrawLogseq]
            [frontend.components.block :as block]
            [frontend.components.page :as page]
            [frontend.handler.whiteboard :refer [add-new-block-shape!
                                                 page-name->tldr! transact-tldr!]]
            [frontend.rum :as r]
            [frontend.search :as search]
            [frontend.state :as state]
            [frontend.util :as util]
            [goog.object :as gobj]
            [rum.core :as rum]))

(def tldraw (r/adapt-class (gobj/get TldrawLogseq "App")))

(def generate-preview (gobj/get TldrawLogseq "generateJSXFromApp"))

(rum/defc page
  [props]
  (page/page {:page-name (gobj/get props "pageName") :whiteboard? true}))

(rum/defc breadcrumb
  [props]
  (block/breadcrumb {} (state/get-current-repo) (uuid (gobj/get props "blockId")) nil))

(rum/defc page-name-link
  [props]
  (block/page-cp {:preview? true} {:block/name (gobj/get props "pageName")}))

(defn create-block-shape-by-id [e]
  (when-let [block (block/get-dragging-block)]
    (let [uuid (:block/uuid block)
          client-x (gobj/get e "clientX")
          client-y (gobj/get e "clientY")]
      (add-new-block-shape! uuid client-x client-y))))

(rum/defc tldraw-app
  [name block-id]
  (let [data (page-name->tldr! name block-id)
        [tln set-tln] (rum/use-state nil)]
    (rum/use-effect!
     (fn []
       (when (and tln name)
         (when-let [^js api (gobj/get tln "api")]
           (if (empty? block-id)
             (. api zoomToFit)
             (do (. api selectShapes block-id)
                 (. api zoomToSelection)))))
       nil) [name block-id tln])
    (when (and name (not-empty (gobj/get data "currentPageId")))
      [:div.draw.tldraw.whiteboard.relative.w-full.h-full
       {:style {:overscroll-behavior "none"}
        :on-blur #(state/set-block-component-editing-mode! false)
        :on-drop create-block-shape-by-id
        ;; wheel -> overscroll may cause browser navigation
        :on-wheel util/stop-propagation}

       (tldraw {:renderers {:Page page
                            :Breadcrumb breadcrumb
                            :PageNameLink page-name-link}
                :searchHandler (comp clj->js vec search/page-search)
                :onMount (fn [app] (set-tln ^js app))
                :onPersist (fn [app]
                             (let [document (gobj/get app "serialized")]
                               (transact-tldr! name document)))
                :model data})])))
