;; Harmonograph on noise
;;
;; Version rendered using BinPixels object (accumulative bins)
;;
;; Eats a lot of memory, rendering is run on all but one cores

(ns generateme.harmonograph
  (:require [clojure2d.core :refer :all]
            [clojure2d.pixels :as p]
            [clojure2d.math :as m]
            [clojure2d.math.random :as r]
            [clojure2d.math.joise :as j]
            [clojure2d.math.vector :as v]
            [clojure2d.extra.glitch :as g]
            [clojure2d.color :as c]
            [clojure2d.core :as core]
            [clojure2d.extra.variations :as vr]
            [clojure.pprint :refer [pprint]])
  (:import [clojure2d.pixels BinPixels]
           [clojure2d.math.vector Vec2 Vec4]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def ^:const ^int width 2048)
(def ^:const ^int height 2048)
(def r [-1.1 1.1 -1.1 1.1])
(def ^:const ^double step 0.003451234) ;; time step
(def ^:const ^int first-step 500000)
(def ^:const ^int steps-per-task 1000000)

(defn unit-fn ^double [_ _] 1.0)

(defn make-jnoise
  "Create own version of joise noise (only simplex based, no cells)"
  []
  (j/make-noise (j/make-fractal {:type (rand-nth (keys j/fractal-type))
                                 :lacunarity (r/drand 1.0 3.0)
                                 :frequency (r/drand 1.0 3.0)
                                 :octaves [[1 (j/make-random-basis-module)]
                                           [1 (j/make-random-basis-module)]]})))


(defn make-random-config
  "Make random harmonograph configuration: f1-f4 frequencies, p1-p4 phases, a1-a4 amplitudes,
  nscalex/nscaley - noise scale, n1-n4 noise functions (or value 1.0), c1-c2 colors, dampsteps"
  []
  (let [^int rngs (r/irand 8 32)
        ^double fct (if (r/brand 0.5)
                      m/TWO_PI
                      (r/drand 2.0 7.0))
        freqs (map #(/ (* 5.0 fct) (double %)) (remove zero? (range (- rngs) rngs (r/irand 1 rngs))))
        ^double a1 (r/drand)
        a2 (r/drand (- 1.0 a1))
        ^double a3 (r/drand)
        a4 (- 1.0 a3)
        n [(make-jnoise) (make-jnoise) j/perlin-noise j/perlin-noise r/noise r/noise r/noise]
        palseq (filter #(> 240 (c/get-luma (first %)) 100) (repeatedly #(:palette (g/color-reducer-machine))))
        pal1 (first palseq)
        pal2 (second palseq)
        c1 (first pal1)
        c2 (rand-nth pal1)
        c3 (first pal2)
        c4 (rand-nth pal2)
        var1-conf (vr/make-random-configuration (r/irand 4))]
    {:var1-conf var1-conf
     :var1 (vr/make-combination var1-conf) 
     :f1 (rand-nth freqs)
     :f2 (rand-nth freqs)
     :f3 (rand-nth freqs)
     :f4 (rand-nth freqs)
     :p1 (r/grand)
     :p2 (r/grand)
     :p3 (r/grand)
     :p4 (r/grand)
     :a1 a1
     :a2 a2
     :a3 a3
     :a4 a4
     :nscalex1 (/ 1.0 ^double (r/drand 0.05 2))
     :nscaley1 (/ 1.0 ^double (r/drand 0.05 2))
     :nscalex2 (/ 1.0 ^double (r/drand 0.05 2))
     :nscaley2 (/ 1.0 ^double (r/drand 0.05 2))
     :n1 (if (r/brand 0.6) (rand-nth n) unit-fn)
     :n2 (if (r/brand 0.6) (rand-nth n) unit-fn)
     :n3 (if (r/brand 0.6) (rand-nth n) unit-fn)
     :n4 (if (r/brand 0.6) (rand-nth n) unit-fn)
     :c1 c1
     :c2 c2
     :c3 c3
     :c4 c4
     :folded (r/brand)
     :dampstepsx (if (r/brand 0.5) (vec (range 0.6 1.2 (/ 1.0 (double (r/irand 1 8))))) nil)
     :dampstepsy (if (r/brand 0.5) (vec (range 0.6 1.2 (/ 1.0 (double (r/irand 1 8))))) nil)}))

(defn iterate-harmonograph
  "Read configuration and do `n` iterations starting at time `start-time`, store everything in `BinPixels`."
  [n start-time run?
   {:keys [^double f1 ^double f2 ^double f3 ^double f4
           ^double p1 ^double p2 ^double p3 ^double p4
           ^double a1 ^double a2 ^double a3 ^double a4
           ^double nscalex1 ^double nscaley1
           ^double nscalex2 ^double nscaley2
           n1 n2 n3 n4
           ^Vec4 c1 ^Vec4 c2 ^Vec4 c3 ^Vec c4
           dampstepsx dampstepsy
           var1 folded]}]
  (let [^int dampxc (if dampstepsx (count dampstepsx) 0)
        ^int dampyc (if dampstepsy (count dampstepsy) 0)
        ^BinPixels bp (p/make-binpixels r width height)]
    (loop [prevx (double 0.0)
           prevy (double 0.0)
           time (double start-time)
           iter (long 0)]
      (if (and @run? (< iter ^long n))
        (let [s1 (m/sin (+ (* time f2) p2))
              s2 (m/sin (+ (* time f3) p3))

              s3 (m/sin (+ (* time f1)
                           (* m/TWO_PI ^double (n1 (* (+ time prevy) nscaley2) p1))
                           p1))
              ^double dampx (if (zero? dampxc) 1.0
                                (dampstepsx (int (m/norm (m/qsin (+ time p1)) -1.0 1.1 0.0 dampxc))))
              ^double dampy (if (zero? dampyc) 1.0
                                (dampstepsy (int (m/norm (m/qsin (+ time p4)) -1.0 1.1 0.0 dampyc))))              

              x (* dampx (+ (* a1 s3)
                            (* a2 s1 ^double (n2 (* (+ time prevx) nscalex1) (* prevy nscaley1)))))
              s4 (m/sin (+ (* time f4)
                           (* m/TWO_PI ^double (n4 (* x nscalex2) p4))
                           p4))
              y (* dampy (+ (* a3 s2 ^double (n3 (* prevx nscalex2) (* prevy nscaley2)))
                            (* a4 s4)))

              ^Vec2 vv (v/applyf (var1 (Vec2. x y)) m/sin)
              p (if folded vv (Vec2. x y))
              
              ^Vec4 col1 (v/interpolate c1 c2 (m/abs (* s1 s2)))
              ^Vec4 col2 (v/interpolate c3 c4 (m/abs (* s3 s4))) 
              ^Vec4 col (v/interpolate col1 col2 (m/qsin time))] 
          
          (p/add-pixel-bilinear bp (.x p) (.y p) (.x col) (.y col) (.z col))
          (recur (.x vv) (.y vv) (+ time step) (inc iter)))
        bp))))

(defn draw-on-canvas
  "Render BinPixels to canvas."
  [canvas ^BinPixels bp]
  (p/set-canvas-pixels canvas (p/to-pixels bp
                                           (Vec4. 8 10 15 255)
                                           {:saturation 1.5 :brightness 1.2 :alpha-gamma 0.6}))  )

;; Create canvas, windows, binpixels, configuration and iterate until window is closed
;; press `space` to save
;; close window to stop
(def result (let [config (make-random-config)
                  canvas (create-canvas width height)
                  [_ run?] (show-window canvas "Harmonograph" 800 800 5)]

              (defmethod key-pressed ["Harmonograph" \space] [_]
                (save-canvas canvas (next-filename "results/ex32/" ".png")))

              (pprint config)
              
              ;; first run
              (let [bp (iterate-harmonograph first-step 0.0 run? config)]

                (draw-on-canvas canvas bp)
                (loop [time (* step first-step)
                       prev bp]
                  (if @run?
                    (do
                      (println time) 
                      (let [newb (reduce #(p/merge-binpixels %1 (deref %2)) prev
                                         (doall (map #(future (iterate-harmonograph steps-per-task (+ time (* step ^int % steps-per-task)) run? config))
                                                     (range available-tasks))))] 
                        (draw-on-canvas canvas newb)
                        (recur (+ time ^double (r/grand) (* step steps-per-task available-tasks))
                               newb)))
                    (do
                      (println :done)
                      prev))))))