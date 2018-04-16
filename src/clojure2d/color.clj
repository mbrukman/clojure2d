;; # Namespace scope
;;
;; Color management functions:
;;
;; * Color is represented by Vec3, Vec4 or java.awt.Color
;; * Blending functions
;; * Color space converters
;; * Palette collections: 200 5-color palettes from colourlovers, paletton colors generator, Inigo Quilez palette generator
;; * Nearest color filter
;;
;; Generally color is represented by Vec4 containing R,G,B,A values from 0 to 255. Three conversion functions are defined by `ColorProto` which extends Vec3, Vec4, Keyword and java.awt.Color types.
;; Functions are:
;;
;; * `to-color` to get Vec4 object
;; * `to-awt-color` to get `java.awt.Color` object
;; * `to-luma` to get brightness as `double`
;;
;; `Vec4` is clojure2d color representation.
;; Keyword can be used when representing one of 140 html/css colornames (https://www.w3schools.com/colors/colors_names.asp). Eg. :linen
;;

(ns clojure2d.color
  (:require [clojure.xml :as xml]
            [fastmath.core :as m]
            [fastmath.random :as r]
            [fastmath.vector :as v]
            [fastmath.stats :as stat]
            [fastmath.interpolation :as i]
            [clojure.java.io :refer :all])
  (:import [fastmath.vector Vec2 Vec3 Vec4]           
           java.awt.Color))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
(m/use-primitive-operators)

;; ## Clamping functions

;; First define some clamping functions

(defn clamp255
  "Clamp to 0-255 double"
  ^double [^double a]
  (m/constrain a 0 255))

(defn lclamp255
  "Clamp to 0-255 long"
  ^long [^double a]
  (m/constrain (m/round a) 0 255))

(defn mod255
  "Leave 8 bits from long. Wraps input to 0-255 integer"
  ^long [^long a]
  (bit-and 0xff a))

(defmacro clamp1
  "Clamp to 0.0-1.0"
  [v]
  `(m/constrain ~v 0.0 1.0))

(defmacro mod1
  "Cut to 0.0-1.0"
  [v]
  `(m/frac ~v))

;; ## Color representation

;; Define `ColorProto` for representation conversions.
(defprotocol ColorProto
  (to-color [c])
  (to-awt-color [c]) 
  (luma [c])
  (red [c])
  (green [c])
  (blue [c])
  (alpha [c])
  (ch0 [c])
  (ch1 [c])
  (ch2 [c]))

(defn- luma-fn
  "Local luma conversion function"
  ^double [^double r ^double g ^double b]
  (+ (* 0.212671 r)
     (* 0.715160 g)
     (* 0.072169 b)))

(declare html-awt-color)
(declare html-color)

;; Equip `Vec3`, `Vec4`, `Keyword` and `java.awt.Color` types with `ColorProto` functions.
;; For keyword use one of 140 names from html/css palettes (list: https://www.w3schools.com/colors/colors_names.asp)
(extend-protocol ColorProto
  Vec3
  (to-color [^Vec3 c]
    (Vec4. (.x c) (.y c) (.z c) 255))
  (to-awt-color [^Vec3 c]
    (Color. (lclamp255 (.x c))
            (lclamp255 (.y c))
            (lclamp255 (.z c))))
  (luma ^double [^Vec3 c] (luma-fn (.x c) (.y c) (.z c)))
  (red [^Vec3 c] (.x c))
  (green [^Vec3 c] (.y c))
  (blue [^Vec3 c] (.z c))
  (ch0 [^Vec3 c] (.x c))
  (ch1 [^Vec3 c] (.y c))
  (ch2 [^Vec3 c] (.z c))
  (alpha [_] 255.0)
  Vec4
  (to-color [c] c)
  (to-awt-color [^Vec4 c]
    (Color.  (lclamp255 (.x c))
             (lclamp255 (.y c))
             (lclamp255 (.z c))
             (lclamp255 (.w c))))
  (luma ^double [^Vec4 c] (luma-fn (.x c) (.y c) (.z c)))
  (red [^Vec4 c] (.x c))
  (green [^Vec4 c] (.y c))
  (blue [^Vec4 c] (.z c))
  (ch0 [^Vec4 c] (.x c))
  (ch1 [^Vec4 c] (.y c))
  (ch2 [^Vec4 c] (.z c))
  (alpha [^Vec4 c] (.w c))
  clojure.lang.Keyword
  (to-color [n] (html-color n))
  (to-awt-color [n] (html-awt-color n))
  (luma [n] (luma (html-color n)))
  (red [n] (red (html-color n)))
  (green [n] (green (html-color n)))
  (blue [n] (blue (html-color n)))
  (ch0 [n] (red (html-color n)))
  (ch1 [n] (green (html-color n)))
  (ch2 [n] (blue (html-color n)))
  (alpha [n] (alpha (html-color n)))
  Color
  (to-color [^Color c]
    (Vec4. (.getRed c)
           (.getGreen c)
           (.getBlue c)
           (.getAlpha c)))
  (to-awt-color [c] c)
  (luma ^double [^Color c] (luma-fn (.getRed c) (.getGreen c) (.getBlue c)))
  (red [^Color c] (.getRed c))
  (green [^Color c] (.getGreen c))
  (blue [^Color c] (.getBlue c))
  (ch0 [^Color c] (.getRed c))
  (ch1 [^Color c] (.getGreen c))
  (ch2 [^Color c] (.getBlue c))
  (alpha [^Color c] (.getAlpha c))
  nil
  (to-color [_] nil)
  (to-awt-color [_] nil)
  Long
  (alpha [^long c] (bit-and 0xff (>> c 24)))
  (red [^long c] (bit-and 0xff (>> c 16)))
  (green [^long c] (bit-and 0xff (>> c 8)))
  (blue [^long c] (bit-and 0xff c))
  (ch0 [^long c] (bit-and 0xff (>> c 16)))
  (ch1 [^long c] (bit-and 0xff (>> c 8)))
  (ch2 [^long c] (bit-and 0xff c))
  (to-color [^long c] (Vec4. (red c) (green c) (blue c) (if (zero? (bit-and 0xff000000 c)) 255 (alpha c))))
  (to-awt-color [c] (to-awt-color (to-color c)))
  (luma [c] (luma (to-color c)))
  String
  (alpha [^String c] (alpha (Long/parseLong c 16)))
  (red [^String c] (red (Long/parseLong c 16)))
  (green [^String c] (green (Long/parseLong c 16)))
  (blue [^String c] (blue (Long/parseLong c 16)))
  (ch0 [^String c] (ch0 (Long/parseLong c 16)))
  (ch1 [^String c] (ch1 (Long/parseLong c 16)))
  (ch2 [^String c] (ch2 (Long/parseLong c 16)))
  (to-color [^String c] (to-color (Long/parseLong c 16)))
  (to-awt-color [c] (to-awt-color (to-color c)))
  (luma [c] (luma (to-color c))))

(defn lerp
  "Lerp color between two values."
  [c1 c2 t]
  (v/interpolate (to-color c1) (to-color c2) t))

(defn set-alpha
  "Set alpha channel and return `Vec4` representation."
  [c a]
  (let [^Vec4 v (to-color c)]
    (Vec4. (.x v) (.y v) (.z v) a)))

(defn set-awt-alpha
  "Set alpha channel and return `Color` representation."
  [c a]
  (let [^Color cc (to-awt-color c)]
    (Color. (.getRed cc)
            (.getGreen cc)
            (.getBlue cc)
            (lclamp255 a))))

(defn awt-color
  "Create java.awt.Color object. Use with `core/set-awt-color` or `core/set-awt-background`."
  ([c]
   (to-awt-color c))
  ([c a]
   (set-awt-alpha c a))
  ([r g b]
   (Color. (lclamp255 r)
           (lclamp255 g)
           (lclamp255 b)))
  ([r g b a]
   (Color. (lclamp255 r)
           (lclamp255 g)
           (lclamp255 b)
           (lclamp255 a))))

(defn color
  "Create Vec4 object as color representation. Use with `core/set-color` or `core/set-background`."
  ([c]
   (to-color c))
  ([c a]
   (set-alpha c a))
  ([r g b]
   (Vec4. (clamp255 r)
          (clamp255 g)
          (clamp255 b)
          255.0))
  ([r g b a]
   (Vec4. (clamp255 r)
          (clamp255 g)
          (clamp255 b)
          (clamp255 a))))

(defn gray
  "Create grayscale color based on intensity `v`. Optional parameter alpha `a`."
  ([v] (color v v v))
  ([v a] (color v v v a)))

(defn awt-gray
  "Create grayscale color based on intensity `v`. Optional parameter alpha `a`.

  AWT version of [[gray]]."
  ([v] (awt-color v v v))
  ([v a] (awt-color v v v a)))

(declare to-HC)
(declare to-HC-polar)

(defn hue
  "Get hue value from color (any representation). Returns angle (0-360).
  
  Uses hexagonal transformation."
  ^double [c]
  (let [^Vec4 ret (to-HC (to-color c))]
    (.x ret)))

(defn hue-polar
  "Get hue value from color (any representation). Returns angle (0-360).
  
  Uses polar transformation."
  ^double [c]
  (let [^Vec4 ret (to-HC-polar (to-color c))]
    (.x ret)))

;; ## Blending / Composing

;; Several color blending / composing functions. Used to compose two images (`Pixels`). See `core.pixels` namespace for filters.

;; Some blending functions require additional parameter. You can set it with following variable.
(def ^:dynamic ^double *blend-threshold* 0.5)

(def ^:const ^double rev255 (/ 255.0))

;; Blend colors functions

(defn blend-values
  "Blend individual values (0-255)"
  [f ^double a ^double b]
  (* 255.0 ^double (f (* rev255 a) (* rev255 b))))

(defn blend-colors
  "Blend colors with blending function. Do not blend alpha on default."
  (^Vec4 [f c1 c2 alpha?]
   (let [^Vec4 cc1 (v/div (to-color c1) 255.0)
         ^Vec4 cc2 (v/div (to-color c2) 255.0)]
     (Vec4. (* 255.0 ^double (f (.x cc1) (.x cc2)))
            (* 255.0 ^double (f (.y cc1) (.y cc2)))
            (* 255.0 ^double (f (.z cc1) (.z cc2)))
            (if alpha?
              (* 255.0 ^double (f (.w cc1) (.w cc2)))
              (* 255.0 (.w cc1))))))
  (^Vec4 [f c1 c2] (blend-colors c1 c2 false)))

;; Plenty of blending functions. Bleding functions operate on 0.0-1.0 values and return new value in the same range.

(defn blend-none
  "Return first value only. Do nothing."
  ^double [a b] a)

(defn blend-add
  "Add"
  ^double [^double a ^double b]
  (clamp1 (+ a b)))

(defn blend-madd
  "Modulus add"
  ^double [^double a ^double b]
  (mod1 (+ a b)))

(defn blend-subtract
  "Subtract"
  ^double [^double a ^double b]
  (clamp1 (- a b)))

(defn blend-msubtract
  "Modulus subtract"
  ^double [^double a ^double b]
  (mod1 (- a b)))

(defn blend-linearburn
  "Linear burn"
  ^double [^double a ^double b]
  (clamp1 (dec (+ a b))))

(defn blend-mlinearburn
  "Modulus linear burn"
  ^double [^double a ^double b]
  (mod1 (dec (+ a b))))

(defn blend-darken
  "Darken"
  ^double [^double a ^double b] 
  (min a b))

(defn blend-lighten
  "Lighten"
  ^double [^double a ^double b]
  (max a b))

(defn blend-multiply
  "Multiply"
  ^double [^double a ^double b]
  (* a b))

(defn blend-screen
  "Screen"
  ^double [^double a ^double b]
  (let [ra (- 1.0 a)
        rb (- 1.0 b)]
    (- 1.0 (* rb ra))))

(defn blend-dodge
  "Dodge"
  ^double [^double a ^double b]
  (clamp1 (/ a (- 1.0 b))))

(defn blend-mdodge
  "Modulus dodge"
  ^double [^double a ^double b]
  (mod1 (/ a (max 0.0001 (- 1.0 b)))))

(defn blend-burn
  "Burn"
  ^double [^double a ^double b]
  (clamp1 (- 1.0 (/ (- 1.0 a) b))))

(defn blend-mburn
  "Modulus burn"
  ^double [^double a ^double b]
  (mod1 (- 1.0 (/ (- 1.0 a) (max 0.0001 b)))))

(defn blend-hardmix
  "Hard mix"
  ^double [^double a ^double b]
  (let [t (- 1.0 b)]
    (cond (< a t) 0.0
          (> a t) 1.0
          :else a)))

(defn blend-linearlight
  "Linear light"
  ^double [^double a ^double b]
  (clamp1 (-> b
              (+ a)
              (+ a)
              (- 1.0))))

(defn blend-mlinearlight
  "Modulus linear light"
  ^double [^double a ^double b]
  (mod1 (-> b
            (+ a)
            (+ a)
            (- 1.0))))

(defn blend-pegtoplight
  "Pegtop light"
  ^double [^double a ^double b]
  (let [ab (* a b)]
    (clamp1 (->> b
                 (- 1.0)
                 (* a a)
                 (+ ab)
                 (+ ab)))))

(defn blend-mpegtoplight
  "Modulus pegtop light"
  ^double [^double a ^double b]
  (let [ab (* a b)]
    (mod1 (->> b
               (- 1.0)
               (* a a)
               (+ ab)
               (+ ab)))))

(defn blend-difference
  "Difference"
  ^double [^double a ^double b]
  (m/abs (- a b)))

(defn blend-divide
  "Divide"
  ^double [^double a ^double b]
  (clamp1 (/ a (+ b m/EPSILON))))

(defn blend-mdivide
  "Modulus divide"
  ^double [^double a ^double b]
  (mod1 (/ a (+ b m/EPSILON))))

(defn blend-or
  "Bitwise or"
  ^double [^double a ^double b]
  (let [aa (unchecked-long (* a 255.0))
        bb (unchecked-long (* b 255.0))]
    (* rev255 (bit-and 0xff (bit-or aa bb)))))

(defn blend-and
  "Bitwise and"
  ^double [^double a ^double b]
  (let [aa (unchecked-long (* a 255.0))
        bb (unchecked-long (* b 255.0))]
    (* rev255 (bit-and 0xff (bit-and aa bb)))))

(defn blend-xor
  "Bitwise xor"
  ^double [^double a ^double b]
  (let [aa (unchecked-long (* a 255.0))
        bb (unchecked-long (* b 255.0))]
    (* rev255 (bit-and 0xff (bit-xor aa bb)))))

(defn blend-exclusion
  "Exclusion"
  ^double [^double a ^double b]
  (let [ab (* a b)]
    (- (+ a b) (+ ab ab))))

(defn blend-pinlight-raw
  "Internal pinlight"
  ^double [^double a ^double b]
  (let [c (- (+ a a) 1.0)]
    (cond (< b c) c
          (bool-and (<= c b) (< b (+ c 1.0))) b
          :else (+ c 1.0))))

(defn blend-pinlight
  "Pinlight"
  ^double [a b]
  (clamp1 (blend-pinlight-raw a b)))

(defn blend-mpinlight
  "Modulus pinlight"
  ^double [a b]
  (mod1 (blend-pinlight-raw a b)))

(defn blend-opacity
  "Opacity (with `*blend-threshold*`)"
  (^double [^double a ^double b ^double thr]
   (m/mlerp a b thr))
  (^double [^double a ^double b]
   (m/lerp a b *blend-threshold*)))

(defn blend-overlay-raw
  "Internal overlay (with `*blend-threshold*`)"
  (^double [^double a ^double b ^double thr]
   (if (< a thr)
     (* 2.0 (* a b))
     (- 1.0 (* 2.0 (* (- 1.0 a) (- 1.0 b))))))
  (^double [a b]
   (blend-overlay-raw a b *blend-threshold*)))

(defn blend-overlay
  "Overlay"
  ^double [a b]
  (clamp1 (blend-overlay-raw a b)))

(defn blend-moverlay
  "Modulus overlay"
  ^double [a b]
  (mod1 (blend-overlay-raw a b)))

(defn blend-hardlight-raw
  "Internal hardlight (with `*blend-threshold*`)"
  (^double [^double a ^double b ^double thr]
   (if (< b thr)
     (* 2.0 (* a b))
     (- 1.0 (* 2.0 (* (- 1.0 a) (- 1.0 b))))))
  (^double [a b]
   (blend-hardlight-raw a b *blend-threshold*)))

(defn blend-hardlight
  "Hardlight"
  ^double [a b]
  (clamp1 (blend-hardlight-raw a b)))

(defn blend-mhardlight
  "Modulus hardlight"
  ^double [a b]
  (mod1 (blend-hardlight-raw a b)))

(defn blend-softlight-raw
  "Internal softlight (with `*blend-threshold*`)"
  (^double [^double a ^double b ^double thr]
   (if (< a thr)
     (->> b
          (* b)
          (- b)
          (* (- (+ a a) 1.0))
          (+ b))
     (-> b
         (m/sqrt)
         (- b)
         (* (- (+ a a) 1.0))
         (+ b))))
  (^double [a b]
   (blend-softlight-raw a b *blend-threshold*)))

(defn blend-softlight
  "Softlight"
  ^double [a b]
  (clamp1 (blend-softlight-raw a b)))

(defn blend-msoftlight
  "Modulus softlight"
  ^double [a b]
  (mod1 (blend-softlight-raw a b)))

(defn blend-vividlight-raw
  "Internal vividlight (with `*blend-threshold*`)"
  (^double [^double a ^double b ^double thr]
   (if (< a thr)
     (- 1.0 (/ (- 1.0 b) (+ (+ a a) m/EPSILON)))
     (->> a
          (- 1.0)
          (* 2.0)
          (+ m/EPSILON)
          (/ b))))
  (^double [a b]
   (blend-vividlight-raw a b *blend-threshold*)))

(defn blend-vividlight
  "Vividlight"
  ^double [a b]
  (clamp1 (blend-vividlight-raw a b)))

(defn blend-mvividlight
  "Modulus vividlight"
  ^double [a b]
  (mod1 (blend-vividlight-raw a b)))

(defn blend-darkthreshold
  "Dark thresholded (with `*blend-threshold*`)"
  (^double [^double a ^double b ^double thr]
   (if (< a thr) a b))
  (^double [a b]
   (blend-darkthreshold a b *blend-threshold*)))

(defn blend-lightthreshold
  "Light thresholded (with `*blend-threshold*`)"
  (^double [^double a ^double b ^double thr]
   (if (> a thr) a b))
  (^double [a b]
   (blend-lightthreshold a b *blend-threshold*)))

;; List of all blend functions stored in `blends` map
(def blends {:none blend-none
             :add blend-add
             :madd blend-madd
             :subtract blend-subtract
             :msubtract blend-msubtract
             :linearburn blend-linearburn
             :mlinearburn blend-mlinearburn
             :multiply blend-multiply
             :darken blend-darken
             :lighten blend-lighten
             :screen blend-screen
             :dodge blend-dodge
             :mdodge blend-mdodge
             :burn blend-burn
             :mburn blend-mburn
             :hardmix blend-hardmix
             :linearlight blend-linearlight
             :mlinearlight blend-mlinearlight
             :pegtoplight blend-pegtoplight
             :mpegtoplight blend-mpegtoplight
             :difference blend-difference
             :divide blend-divide
             :mdivide blend-mdivide
             :or blend-or
             :and blend-and
             :xor blend-xor
             :exclusion blend-exclusion
             :pinlight blend-pinlight
             :mpinlight blend-mpinlight
             :opacity blend-opacity
             :overlay blend-overlay
             :moverlay blend-moverlay
             :hardlight blend-hardlight
             :mhardlight blend-hardlight
             :softlight blend-softlight
             :msoftlight blend-msoftlight
             :vividlight blend-vividlight
             :mvividlight blend-mvividlight
             :darkthreshold blend-darkthreshold
             :lightthreshold blend-lightthreshold})

;; All names as list
(def blends-names (keys blends))

;; ## Colorspace functions
;;
;; Conversion from RGB to specific color space always converts to range 0-255
;; Reverse conversion is not normalized and can exceed 0-255 range

(defn- test-colors
  "to remove, check ranges"
  [f]
  (loop [cc (int 0)
         mnr (double Integer/MAX_VALUE)
         mxr (double Integer/MIN_VALUE)
         mng (double Integer/MAX_VALUE)
         mxg (double Integer/MIN_VALUE)
         mnb (double Integer/MAX_VALUE)
         mxb (double Integer/MIN_VALUE)]
    (let [r (bit-and 0xff (bit-shift-right cc 16))
          g (bit-and 0xff (bit-shift-right cc 8))
          b (bit-and 0xff cc)
          ^Vec4 res (f (Vec4. r g b 255))
          nmnr (if (< (.x res) mnr) (.x res) mnr)
          nmxr (if (> (.x res) mxr) (.x res) mxr)
          nmng (if (< (.y res) mng) (.y res) mng)
          nmxg (if (> (.y res) mxg) (.y res) mxg)
          nmnb (if (< (.z res) mnb) (.z res) mnb)
          nmxb (if (> (.z res) mxb) (.z res) mxb)]
      (if (< cc 0x1000000)
        (recur (inc cc) (double nmnr) (double nmxr) (double nmng) (double nmxg) (double nmnb) (double nmxb))
        {:min-r nmnr :max-r nmxr :min-g nmng :max-g nmxg :min-b nmnb :max-b nmxb}))))

;; ### CMY

(defn to-CMY
  "RGB -> CMY"
  [^Vec4 c]
  (Vec4. (- 255.0 (.x c))
         (- 255.0 (.y c))
         (- 255.0 (.z c))
         (.w c)))

(def from-CMY to-CMY)

;; ### OHTA

(defn to-OHTA
  "RGB -> OHTA, normalized"
  [^Vec4 c]
  (let [i1 (/ (+ (.x c) (.y c) (.z c)) 3.0)
        i2 (/ (+ 255.0 (- (.x c) (.z c))) 2.0)
        i3 (/ (+ 510.0 (.x c) (.z c) (- (+ (.y c) (.y c)))) 4.0)]
    (Vec4. i1 i2 i3 (.w c))))

(def ^:private ^:const ^double c46 (/ 4.0 6.0))

(defn from-OHTA
  "OHTA -> RGB"
  [^Vec4 c]
  (let [i1 (.x c) ; divided by 3
        i2 (- (.y c) 127.5) ; divided by 2
        i3 (- (* c46 (.z c)) 85.0) ; divided by 6
        r (+ i1 i2 i3)
        g (- i1 i3 i3)
        b (- (+ i1 i3) i2)]
    (Vec4. r g b (.w c))))

;; ### sRGB

(def ^:private ^:const ^double gamma-factor (/ 2.4))

(defn to-linear
  "Gamma correction"
  ^double [^double v]
  (if (> v 0.04045)
    (m/pow (/ (+ 0.055 v) 1.055) 2.4)
    (/ v 12.92)))

(defn from-linear
  "Gamma correction"
  ^double [^double v]
  (if (> v 0.0031308)
    (- (* 1.055 (m/pow v gamma-factor)) 0.055)
    (* v 12.92)))

(defn to-sRGB
  "Linear RGB to non-linear sRGB"
  [^Vec4 c]
  (v/vec4 (-> (Vec3. (.x c) (.y c) (.z c))
              (v/div 255.0)
              (v/applyf from-linear)
              (v/mult 255.0))
          (.w c)))

(defn from-sRGB
  "Non-linear sRGB to linear RGB"
  [^Vec4 c]
  (v/vec4 (-> (Vec3. (.x c) (.y c) (.z c))
              (v/div 255.0)
              (v/applyf to-linear)
              (v/mult 255.0))
          (.w c)))

;; ### XYZ

(def ^:private ^:const ^double xyz-xmax 0.9504716671128306)
(def ^:private ^:const ^double xyz-ymax 0.9999570331323426)
(def ^:private ^:const ^double xyz-zmax 1.0889782052041752)

(defn to-XYZ-raw
  "Pure RGB->XYZ conversion without corrections."
  ^Vec3 [^Vec3 c]
  (Vec3. (+ (* (.x c) 0.41239558896741421610) (* (.y c) 0.35758343076371481710) (* (.z c) 0.18049264738170157350))
         (+ (* (.x c) 0.21258623078559555160) (* (.y c) 0.71517030370341084990) (* (.z c) 0.07220049864333622685))
         (+ (* (.x c) 0.01929721549174694484) (* (.y c) 0.11918386458084853180) (* (.z c) 0.95049712513157976600))))

(defn- to-XYZ-
  "RGB->XYZ with corrections"
  [^Vec4 c]
  (let [xyz-raw (to-XYZ-raw (-> (Vec3. (.x c) (.y c) (.z c))
                                (v/div 255.0)
                                (v/applyf to-linear)))]
    (v/vec4 xyz-raw (.w c))))

(defn to-XYZ
  "Normlized RGB->XYZ"
  [c]
  (let [^Vec4 cc (to-XYZ- c)]
    (Vec4. (m/norm (.x cc) 0.0 xyz-xmax 0.0 255.0)
           (m/norm (.y cc) 0.0 xyz-ymax 0.0 255.0)
           (m/norm (.z cc) 0.0 xyz-zmax 0.0 255.0)
           (.w cc))))

(defn from-XYZ-raw
  "Pure XYZ->RGB conversion."
  ^Vec3 [^Vec3 v]
  (Vec3. (+ (* (.x v)  3.2406) (* (.y v) -1.5372) (* (.z v) -0.4986))
         (+ (* (.x v) -0.9689) (* (.y v)  1.8758) (* (.z v)  0.0415))
         (+ (* (.x v)  0.0557) (* (.y v) -0.2040) (* (.z v)  1.0570))))

(defn- from-XYZ-
  "XYZ->RGB conversion with corrections"
  [^Vec4 c]
  (let [^Vec3 rgb-raw (v/mult (v/applyf (from-XYZ-raw (Vec3. (.x c) (.y c) (.z c))) from-linear) 255.0)]
    (v/vec4 rgb-raw (.w c))))

(defn from-XYZ
  "XYZ->RGB normalized"
  [^Vec4 c]
  (let [x (m/norm (.x c) 0.0 255.0 0.0 xyz-xmax)
        y (m/norm (.y c) 0.0 255.0 0.0 xyz-ymax)
        z (m/norm (.z c) 0.0 255.0 0.0 xyz-zmax)]
    (from-XYZ- (Vec4. x y z (.w c)))))

;; ### LUV

(def ^:const ^double D65X 0.950456)
(def ^:const ^double D65Z 1.088754)
(def ^:const ^double CIEEpsilon (/ 216.0 24389.0))
(def ^:const ^double CIEK (/ 24389.0 27.0))
(def ^:const ^double OneThird (/ 1.0 3.0))
(def ^:const ^double D65FX-4 (/ (* 4.0 D65X) (+ D65X 15.0 (* 3.0 D65Z))))
(def ^:const ^double D65FY-9 (/ 9.0 (+ D65X 15.0 (* 3.0 D65Z))))

(defn- perceptible-reciprocal
  "LUV reciprocal"
  ^double [^double x]
  (if (>= (m/abs x) m/EPSILON)
    (/ x)
    (/ (m/sgn x) m/EPSILON)))

(defn to-LUV
  "RGB->LUV normalized"
  [^Vec4 c]
  (let [^Vec4 xyz (to-XYZ- c)
        L (if (> (.y xyz) CIEEpsilon)
            (- (* 116.0 (m/pow (.y xyz) OneThird)) 16.0)
            (* (.y xyz) CIEK))
        alpha (perceptible-reciprocal (+ (.x xyz) (* 15.0 (.y xyz)) (* 3.0 (.z xyz))))
        L13 (* L 13.0)
        u (* L13 (- (* 4.0 alpha (.x xyz)) D65FX-4))
        v (* L13 (- (* 9.0 alpha (.y xyz)) D65FY-9))
        L (/ L 100.0)
        u (/ (+ u 134.0) 354.0)
        v (/ (+ v 140.0) 262.0)]
    (Vec4. (m/norm L 0.0 0.9999833859065517 0.0 255.0) 
           (m/norm u 0.1438470144487729 0.8730615053231279 0.0 255.0)
           (m/norm v 0.022447496915761492 0.944255184334379 0.0 255.0)
           (.w c))))

(def ^:const ^double CIEK2Epsilon (* CIEK CIEEpsilon))

(defn from-LUV
  "LUV->RGB normalized"
  [^Vec4 c]
  (let [L (* 100.0 ^double (m/norm (.x c) 0 255 0.0 0.9999833859065517))
        u (- (* 354.0 ^double (m/norm (.y c) 0 255 0.1438470144487729 0.8730615053231279)) 134.0)
        v (- (* 262.0 ^double (m/norm (.z c) 0 255 0.022447496915761492 0.944255184334379)) 140.0)
        Y (if (> L CIEK2Epsilon)
            (m/pow (/ (+ L 16.0) 116.0) 3.0)
            (/ L CIEK))
        L13 (* 13.0 L)
        L52 (* 52.0 L)
        Y5 (* 5.0 Y)
        L13u (-> L52
                 (/ (+ u (* L13 D65FX-4)))
                 dec
                 (/ 3.0))
        X (/ (+ Y5 (* Y (- (/ (* 39.0 L) (+ v (* L13 D65FY-9))) 5.0))) (+ L13u OneThird))
        Z (- (* X L13u) Y5)]
    (from-XYZ- (Vec4. X Y Z (.w c)))))

;; ### LAB

(defn- to-lab-correct
  "LAB correction"
  ^double [^double v]
  (if (> v CIEEpsilon)
    (m/pow v OneThird)
    (/ (+ 16.0 (* v CIEK)) 116.0)))

(defn to-LAB
  "RGB->LAB normalized"
  [^Vec4 c]
  (let [^Vec4 xyz (to-XYZ- c)
        x (/ (.x xyz) D65X)
        y (.y xyz)
        z (/ (.z xyz) D65Z)
        x (to-lab-correct x)
        y (to-lab-correct y)
        z (to-lab-correct z)
        L (/ (- (* y 116.0) 16.0) 100.0)
        a (+ 0.5 (/ (* 500.0 (- x y)) 255.0))
        b (+ 0.5 (/ (* 200.0 (- y z)) 255.0))]
    (Vec4. (m/norm L 0.0 0.9999833859065517 0.0 255.0)
           (m/norm a 0.16203039020156618 0.8853278445843099 0.0 255.0)
           (m/norm b 0.07698923890750631 0.8705163895243013 0.0 255.0) 
           (.w c))))

(defn from-lab-correct
  "LAB correction"
  ^double [^double v]
  (let [v3 (* v v v)]
    (if (> v3 CIEEpsilon)
      v3
      (/ (- (* 116.0 v) 16.0) CIEK))))

(defn from-LAB
  "LAB->RGB normalized"
  [^Vec4 c]
  (let [L (* 100.0 (m/norm (.x c) 0.0 255.0 0.0 0.9999833859065517))
        a (m/norm (.y c) 0.0 255.0 0.16203039020156618 0.8853278445843099)
        b (m/norm (.z c) 0.0 255.0 0.07698923890750631 0.8705163895243013)
        y (/ (+ L 16.0) 116.0)
        x (* D65X (from-lab-correct (+ y (/ (* 255.0 (- a 0.5)) 500.0))))
        z (* D65Z (from-lab-correct (- y (/ (* 255.0 (- b 0.5)) 200.0))))
        y3 (* y y y)
        y (if (> y3 CIEEpsilon)
            y3
            (/ L CIEK))]
    (from-XYZ- (Vec4. x y z (.w c)))))

;; ### YXy (xyY)

(defn to-YXY
  "RGB->YXY"
  [^Vec4 c]
  (let [^Vec4 xyz (to-XYZ- c)
        d (+ (.x xyz) (.y xyz) (.z xyz))
        Y (m/norm (.y xyz) 0.0 0.9999570331323426 0.0 255.0)
        x (m/norm (/ (.x xyz) d) 0.150011724420108 0.6400884809339611 0.0 255.0)
        y (m/norm (/ (.y xyz) d) 0.060007548576610774 0.6000064972148145 0.0 255.0)]
    (Vec4. Y x y (.w c))))

(defn from-YXY
  "YXY->RGB"
  [^Vec4 c]
  (let [Y (m/norm (.x c) 0.0 255.0 0.0 0.9999570331323426)
        x (m/norm (.y c) 0.0 255.0 0.150011724420108 0.6400884809339611)
        y (m/norm (.z c) 0.0 255.0 0.060007548576610774 0.6000064972148145)
        Yy (/ Y y)
        X (* x Yy)
        Z (* (- 1.0 x y) Yy)] (from-XYZ- (Vec4. X Y Z (.w c)))))

;; Hue based

(defn- to-HC
  "Calculate hue and chroma"
  [^Vec4 c]
  (let [M (max (.x c) (.y c) (.z c))
        m (min (.x c) (.y c) (.z c))
        C (- M m)
        ^double h (if (zero? C)
                    0.0
                    (let [rC (/ C)]
                      (cond
                        (== M (.x c)) (mod (* rC (- (.y c) (.z c))) 6.0)
                        (== M (.y c)) (+ 2.0 (* rC (- (.z c) (.x c))))
                        :else (+ 4.0 (* rC (- (.x c) (.y c)))))))]
    (Vec4. (* 60.0 h) C M m)))

;; maxh 359.80503179148246
(defn- to-HC-polar
  "Calculate hue and chroma - polar version"
  [^Vec4 c]
  (let [a (* 0.5 (- (+ (.x c) (.x c)) (.y c) (.z c)))
        b (* 0.8660254037844386 (- (.y c) (.z c)))
        h (m/degrees (m/atan2 b a))
        c2 (m/hypot-sqrt a b)]
    (Vec4. (if (neg? h) (+ 360.0 h) h) c2 (max (.x c) (.y c) (.z c)) (min (.x c) (.y c) (.z c)))))

(defn- from-HCX
  "Convert HCX to RGB"
  [^double h ^double c ^double x]
  (cond
    (<= 0.0 h 1.0) (Vec3. c x 0.0)
    (<= 1.0 h 2.0) (Vec3. x c 0.0)
    (<= 2.0 h 3.0) (Vec3. 0.0 c x)
    (<= 3.0 h 4.0) (Vec3. 0.0 x c)
    (<= 4.0 h 5.0) (Vec3. x 0.0 c)
    :else (Vec3. c 0.0 x)))

(def ^:private ^:const ^double n360->255 (/ 255.0 359.7647058823529))
(def ^:private ^:const ^double n255->360 (/ 359.7647058823529 255.0))

(defn- normalize-HSx
  "Make output range 0-255"
  [^Vec4 c]
  (Vec4. (* n360->255 (.x c))
         (* 255.0 (.y c))
         (* 255.0 (.z c))
         (.w c)))

(defn- denormalize-HSx 
  "Make output range native to HSx colorspaces"
  [^Vec4 c]
  (Vec4. (* n255->360 (.x c))
         (/ (.y c) 255.0)
         (/ (.z c) 255.0)
         (.w c)))

;; HSI

(defn to-HSI-raw
  "RGB -> HSI

  Output ranges:

  * H - 0.0 - 360.0
  * S - 0.0 - 1.0
  * I - 0.0 - 1.0"
  [^Vec4 c]
  (let [^Vec4 hc (to-HC c)
        I (/ (+ (.x c) (.y c) (.z c)) 3.0)
        S (if (zero? I) 0.0
              (- 1.0 (/ (.w hc) I)))]
    (Vec4. (.x hc) S (/ I 255.0) (.w c))))

(defn from-HSI-raw
  "HSI -> RGB"
  [^Vec4 c]
  (let [h' (/ (.x c) 60.0)
        z (- 1.0 (m/abs (dec (mod h' 2.0))))
        C (/ (* 3.0 (.z c) (.y c)) (inc z))
        X (* C z)
        m (* (.z c) (- 1.0 (.y c)))
        rgb' (v/add (from-HCX h' C X) (Vec3. m m m))]
    (v/vec4 (v/mult rgb' 255.0) (.w c))))

(def ^{:doc "RGB -> HSI, normalized"} to-HSI (comp normalize-HSx to-HSI-raw))
(def ^{:doc "HSI -> RGB, normalized"} from-HSI (comp from-HSI-raw denormalize-HSx))

;; HSV

(defn to-HSV-raw
  "RGB -> HSV
  
  Output ranges:

  * H - 0.0 - 360.0
  * S - 0.0 - 1.0
  * V - 0.0 - 1.0"
  [^Vec4 c]
  (let [^Vec4 hc (to-HC c)
        V (.z hc)
        S (if (zero? V) 0.0
              (/ (.y hc) V))]
    (Vec4. (.x hc) S (/ V 255.0) (.w c))))

(defn from-HSV-raw
  "HSV -> RGB"
  [^Vec4 c]
  (let [C (* (.y c) (.z c))
        h' (/ (.x c) 60.0)
        X (* C (- 1.0 (m/abs (dec (mod h' 2.0)))))
        m (- (.z c) C)
        ^Vec3 rgb' (v/add (from-HCX h' C X) (Vec3. m m m))]
    (v/vec4 (v/mult rgb' 255.0) (.w c))))

(def ^{:doc "RGB -> HSV, normalized"} to-HSV (comp normalize-HSx to-HSV-raw))
(def ^{:doc "HSV -> RGB, normalized"} from-HSV (comp from-HSV-raw denormalize-HSx))

;; HSL

(defn to-HSL-raw
  "RGB -> HSL
  
  Output ranges:

  * H - 0.0 - 360.0
  * S - 0.0 - 1.0
  * L - 0.0 - 1.0"
  [^Vec4 c]
  (let [^Vec4 hc (to-HC c)
        L (/ (* 0.5 (+ (.z hc) (.w hc))) 255.0)
        S (if (== 1.0 L) 0.0
              (/ (.y hc) (- 1.0 (m/abs (dec (+ L L))))))]
    (Vec4. (.x hc) (/ S 255.0) L (.w c))))

(defn from-HSL-raw
  "HSL -> RGB"
  [^Vec4 c]
  (let [C (* (.y c) (- 1.0 (m/abs (dec (+ (.z c) (.z c))))))
        h' (/ (.x c) 60.0)
        X (* C (- 1.0 (m/abs (dec (mod h' 2.0)))))
        m (- (.z c) (* 0.5 C))
        ^Vec3 rgb' (v/add (from-HCX h' C X) (Vec3. m m m))]
    (v/vec4 (v/mult rgb' 255.0) (.w c))))

(def ^{:doc "RGB -> HSL, normalized"} to-HSL (comp normalize-HSx to-HSL-raw))
(def ^{:doc "HSL -> RGB, normalized"} from-HSL (comp from-HSL-raw denormalize-HSx))

;; HCL

(defn to-HCL-raw
  "RGB -> HCL
  
  Output ranges:

  * H - 0.0 - 360.0
  * C - 0.0 - 1.0
  * L - 0.0 - 1.0"
  [^Vec4 c]
  (let [^Vec4 hc (to-HC c)
        L (+ (* 0.298839 (.x c)) (* 0.586811 (.y c)) (* 0.114350 (.z c)))]
    (Vec4. (.x hc) (/ (.y hc) 255.0) (/ L 255.0) (.w c))))

(defn from-HCL-raw
  "HCL -> RGB"
  [^Vec4 c]
  (let [h' (/ (.x c) 60.0)
        X (* (.y c) (- 1.0 (m/abs (dec (mod h' 2.0)))))
        ^Vec3 rgb' (v/add (from-HCX h' (.y c) X))
        m (- (.z c) (* 0.298839 (.x rgb')) (* 0.586811 (.y rgb')) (* 0.114350 (.z rgb')))]
    (v/vec4 (v/mult (v/add rgb' (Vec3. m m m)) 255.0) (.w c))))

(def ^{:doc "RGB -> HCL, normalized"} to-HCL (comp normalize-HSx to-HCL-raw))
(def ^{:doc "HCL -> RGB, normalized"} from-HCL (comp from-HCL-raw denormalize-HSx))

;; HSB = HSV

(def ^{:doc "RGB -> HSB(V), normalized (see [[to-HSV]])"} to-HSB to-HSV)
(def ^{:doc "HSB(V) -> RGB, normalized (see [[from-HSV]])"} from-HSB from-HSV)
(def ^{:doc "RGB -> HSB(V) (see [[to-HSV-raw]])"} to-HSB-raw to-HSV-raw)
(def ^{:doc "HSB(V) -> RGB (see [[from-HSV-raw]])"} from-HSB-raw from-HSV-raw)

;; ### HWB

(defn to-HWB
  "RGB -> HWB, normalized

  Hue range is from 1.0 to 255.0. Values less than 1.0 is considered as undefined."
  [^Vec4 c]
  (let [w (min (.x c) (.y c) (.z c))
        v (max (.x c) (.y c) (.z c))
        h (if (== w v) 0.0
              (let [^double f (condp m/eq w
                                (.x c) (- (.y c) (.z c))
                                (.y c) (- (.z c) (.x c))
                                (.z c) (- (.x c) (.y c)))
                    ^double p (condp m/eq w
                                (.x c) 3.0
                                (.y c) 5.0
                                (.z c) 1.0)]
                (m/norm (/ (- p (/ f (- v w))) 6.0) 0.0 1.0 1.0 255.0)))]
    (Vec4. h w (- 255.0 v) (.w c))))

(defn from-HWB
  "HWB -> RGB, normalized"
  [^Vec4 c]
  (if (< (.x c) 1.0) 
    (let [v (- 255.0 (.z c))]
      (Vec4. v v v (.w c)))
    (let [h (m/norm (.x c) 1.0 255.0 0.0 6.0)
          v (- 1.0 (/ (.z c) 255.0))
          w (/ (.y c) 255.0)
          i (unchecked-int (m/floor h))
          f (- h i)
          f (if (odd? (int i)) (- 1.0 f) f)
          n (+ w (* f (- v w)))
          rgb (case i
                0 (Vec3. v n w)
                1 (Vec3. n v w)
                2 (Vec3. w v n)
                3 (Vec3. w n v)
                4 (Vec3. n w v)
                5 (Vec3. v w n)
                6 (Vec3. v n w))]
      (v/vec4 (v/mult rgb 255.0) (.w c)))))

;; ### GLHS
;;
;; Color Theory and Modeling for Computer Graphics, Visualization, and Multimedia Applications (The Springer International Series in Engineering and Computer Science) by Haim Levkowitz

;; Page 79, minimizer
(def ^:const ^double weight-max 0.7)
(def ^:const ^double weight-mid 0.1)
(def ^:const ^double weight-min 0.2)

(defn to-GLHS-raw
  "RGB -> GLHS

  Output ranges:

  * L - 0.0 - 255.0
  * H - 0.0 - 360.0
  * S - 0.0 - 1.0"
  [^Vec4 c]
  (let [mx (max (.x c) (.y c) (.z c))
        md (stat/median-3 (.x c) (.y c) (.z c))
        mn (min (.x c) (.y c) (.z c))]
    (if (== mx mn)
      (Vec4. mx 0 0 (.w c))
      (let [l (+ (* weight-max mx) (* weight-mid md) (* weight-min mn))
            r (/ (- mx mn))
            e (* (- md mn) r)
            ^long k (cond
                      (bool-and (> (.x c) (.y c)) (>= (.y c) (.z c))) 0
                      (bool-and (>= (.y c) (.x c)) (> (.x c) (.z c))) 1
                      (bool-and (> (.y c) (.z c)) (>= (.z c) (.x c))) 2
                      (bool-and (>= (.z c) (.y c)) (> (.y c) (.x c))) 3
                      (bool-and (> (.z c) (.x c)) (>= (.x c) (.y c))) 4
                      :else 5)
            f (if (even? k)
                e
                (* (- mx md) r))
            h (* 60.0 (+ k f))
            lq (* 255.0 (+ (* weight-mid e) weight-max))
            s (if (<= l lq)
                (/ (- l mn) l)
                (/ (- mx l) (- 255.0 l)))]
        (Vec4. l h s (.w c))))))

(defn to-GLHS
  "RGB -> GLHS, normalized"
  [c]
  (let [^Vec4 cc (to-GLHS-raw c)]
    (Vec4. (.x cc)
           (m/norm (.y cc) 0.0 359.7647058823529 0.0 255.0)
           (* 255.0 (.z cc))
           (.w cc))))

(defn from-GLHS-raw
  "GLHS -> RGB"
  [^Vec4 c]
  (if (zero? (.z c))
    (Vec4. (.x c) (.x c) (.x c) (.w c))
    (let [h (/ (.y c) 60.0)
          k (long (m/floor h))
          f (- h k)
          fp (if (even? k) f (- 1.0 f))
          wfw (+ (* weight-mid fp) weight-max)
          lq (* 255.0 wfw)
          s (.z c)
          ^Vec3 rgb (if (<= (.x c) lq)
                      (let [mn (* (- 1.0 s) (.x c))
                            md (/ (+ (* fp (.x c)) (* mn (- (* (- 1.0 fp) weight-max) (* fp weight-min)))) wfw)
                            mx (/ (- (- (.x c) (* md weight-mid)) (* mn weight-min)) weight-max)]
                        (Vec3. mn md mx))
                      (let [mx (+ (* s 255.0) (* (- 1.0 s) (.x c)))
                            md (/ (- (* (- 1.0 fp) (.x c)) (* mx (- (* (- 1.0 fp) weight-max) (* fp weight-min))))
                                  (+ (* (- 1.0 fp) weight-mid) weight-min))
                            mn (/ (- (- (.x c) (* mx weight-max)) (* md weight-mid)) weight-min)]
                        (Vec3. mn md mx)))]
      (case k
        0 (Vec4. (.z rgb) (.y rgb) (.x rgb) (.w c))
        1 (Vec4. (.y rgb) (.z rgb) (.x rgb) (.w c))
        2 (Vec4. (.x rgb) (.z rgb) (.y rgb) (.w c))
        3 (Vec4. (.x rgb) (.y rgb) (.z rgb) (.w c))
        4 (Vec4. (.y rgb) (.x rgb) (.z rgb) (.w c))
        5 (Vec4. (.z rgb) (.x rgb) (.y rgb) (.w c))
        6 (Vec4. (.z rgb) (.y rgb) (.x rgb) (.w c))))))

(defn from-GLHS
  "GLHS -> RGB"
  [^Vec4 c]
  (from-GLHS-raw (Vec4. (.x c)
                        (m/norm (.y c) 0.0 255.0 0.0 359.7647058823529)
                        (/ (.z c) 255.0)
                        (.w c))))

;; ### YPbPr

(defn to-YPbPr-raw
  "RGB -> YPbPr

  Output range:

  * Y - 0.0 - 255.0
  * Pb - -236.59 - 236.59
  * Pr - -200.79 - 200.79"
  [^Vec4 c]
  (let [y (+ (* 0.2126 (.x c))
             (* 0.7152 (.y c))
             (* 0.0722 (.z c)))
        pb (- (.z c) y)
        pr (- (.x c) y)]
    (Vec4. y pb pr (.w c))))

(defn to-YPbPr
  "RGB -> YPbPr, normalized"
  [c]
  (let [^Vec4 cc (to-YPbPr-raw c)]
    (Vec4. (.x cc)
           (m/norm (.y cc) -236.589 236.589 0.0 255.0)
           (m/norm (.z cc) -200.787 200.787 0.0 255.0)
           (.w cc))))

(defn from-YPbPr-raw
  "YPbPr -> RGB"
  [^Vec4 c]
  (let [b (+ (.x c) (.y c))
        r (+ (.x c) (.z c))
        g (/ (- (.x c) (* 0.2126 r) (* 0.0722 b)) 0.7152)]
    (Vec4. r g b (.w c))))

(defn from-YPbPr
  "YPbPr -> RGB, normalized"
  [^Vec4 c]
  (from-YPbPr-raw (Vec4. (.x c)
                         (m/norm (.y c) 0.0 255.0 -236.589 236.589)
                         (m/norm (.z c) 0.0 255.0 -200.787 200.787)
                         (.w c))))

;; ### YDbDr

(defn to-YDbDr-raw
  "RGB -> YDbDr

  Output range:

  * Y - 0.0 - 255.0
  * Db - -339.915 - 339.915
  * Dr - -339.915 - 339.915"
  [^Vec4 c]
  (let [Y (+ (* 0.299 (.x c)) (* 0.587 (.y c)) (* 0.114 (.z c)))
        Db (+ (* -0.45 (.x c)) (* -0.883 (.y c)) (* 1.333 (.z c)))
        Dr (+ (* -1.333 (.x c)) (* 1.116 (.y c)) (* 0.217 (.z c)))]
    (Vec4. Y Db Dr (.w c))))

(defn to-YDbDr
  "RGB -> YDbDr"
  [c]
  (let [^Vec4 cc (to-YDbDr-raw c)]
    (Vec4. (.x cc)
           (m/norm (.y cc) -339.91499999999996 339.91499999999996 0.0 255.0)
           (m/norm (.z cc) -339.91499999999996 339.915 0.0 255.0)
           (.w cc))))

(defn from-YDbDr-raw
  "YDbDr -> RGB"
  [^Vec4 c]
  (let [Y (.x c)
        Db (.y c)
        Dr (.z c)
        r (+ Y (* 9.2303716147657e-05 Db) (* -0.52591263066186533 Dr))
        g (+ Y (* -0.12913289889050927 Db) (* 0.26789932820759876 Dr))
        b (+ Y (* 0.66467905997895482 Db) (* -7.9202543533108e-05 Dr))]
    (Vec4. r g b (.w c))))

(defn from-YDbDr
  "YDbDr -> RGB, normalized"
  [^Vec4 c]
  (from-YDbDr-raw (Vec4. (.x c)
                         (m/norm (.y c) 0.0 255.0 -339.91499999999996 339.91499999999996)
                         (m/norm (.z c) 0.0 255.0 -339.91499999999996 339.915)
                         (.w c))))


;; ### YCbCr

;; JPEG version

(def ^:private ^:const y-norm (v/vec4 0.0 127.5 127.5 0.0))

(defn to-YCbCr-raw
  "RGB -> YCbCr, normalized"
  [^Vec4 c]
  (let [Y (+ (* 0.298839 (.x c)) (* 0.586811 (.y c)) (* 0.114350 (.z c)))
        Cb (+ (* -0.168736 (.x c)) (* -0.331264 (.y c)) (* 0.5 (.z c)))
        Cr (+ (* 0.5 (.x c)) (* -0.418688 (.y c)) (* -0.081312 (.z c)))]
    (Vec4. Y Cb Cr (.w c))))

(defn to-YCbCr
  "RGB -> YCbCr, normalized"
  [c]
  (v/add (to-YCbCr-raw c) y-norm))

(defn from-YCbCr-raw
  "YCbCr -> RGB"
  [^Vec4 c]
  (let [Cb (.y c)
        Cr (.z c)
        r (+ (* 0.99999999999914679361 (.x c)) (* -1.2188941887145875e-06 Cb) (* 1.4019995886561440468 Cr))
        g (+ (* 0.99999975910502514331 (.x c)) (* -0.34413567816504303521 Cb) (* -0.71413649331646789076 Cr))
        b (+ (* 1.00000124040004623180 (.x c)) (* 1.77200006607230409200 Cb) (* 2.1453384174593273e-06 Cr))]
    (Vec4. r g b (.w c))))

(defn from-YCbCr
  "YCbCr -> RGB, normalized"
  [c]
  (from-YCbCr-raw (v/sub c y-norm)))

;; ### YUV

(defn to-YUV-raw
  "RGB -> YUV

  Output range:

  * Y - 0.0 - 255.0
  * U - -111.18 - 111.18
  * V - -156.83 - 156.83"
  [^Vec4 c]
  (Vec4. (+ (* 0.298839 (.x c)) (* 0.586811 (.y c)) (* 0.114350 (.z c)))
         (+ (* -0.147 (.x c)) (* -0.289 (.y c)) (* 0.436 (.z c)))
         (+ (* 0.615 (.x c)) (* -0.515 (.y c)) (* -0.1 (.z c)))
         (.w c)))

(defn to-YUV
  "RGB -> YUV, normalized"
  [c]
  (let [^Vec4 cc (to-YUV-raw c)]
    (Vec4. (.x cc)
           (m/norm (.y cc) -111.17999999999999 111.17999999999999 0.0 255.0)
           (m/norm (.z cc) -156.82500000000002 156.825 0.0 255.0)
           (.w cc))))

(defn from-YUV-raw
  "YUV -> RGB"
  [^Vec4 c]
  (let [Y (.x c)
        U (.y c)
        V (.z c)
        r (+ Y (* -3.945707070708279e-05 U) (* 1.1398279671717170825 V))
        g (+ Y (* -0.3946101641414141437 U) (* -0.5805003156565656797 V))
        b (+ Y (* 2.0319996843434342537 U) (* -4.813762626262513e-04 V))]
    (Vec4. r g b (.w c))))

(defn from-YUV
  "YUV -> RGB, normalized"
  [^Vec4 c] 
  (from-YUV-raw (Vec4. (.x c)
                       (m/norm (.y c) 0.0 255.0 -111.17999999999999 111.17999999999999)
                       (m/norm (.z c) 0.0 255.0 -156.82500000000002 156.825)
                       (.w c))))

;; ### YIQ

(defn to-YIQ-raw
  "RGB -> YIQ

  Output range:

  * Y - 0.0 - 255.0
  * I - -151.9 - 151.9
  * Q - -133.3 - 133.3"
  [^Vec4 c]
  (Vec4. (+ (* 0.298839 (.x c)) (* 0.586811 (.y c)) (* 0.114350 (.z c)))
         (+ (* 0.595716 (.x c)) (* -0.274453 (.y c)) (* -0.321263 (.z c)))
         (+ (* 0.211456 (.x c)) (* -0.522591 (.y c)) (* 0.311135 (.z c)))
         (.w c)))

(defn to-YIQ
  "RGB -> YIQ, normalized"
  [c]
  (let [^Vec4 cc (to-YIQ-raw c)]
    (Vec4. (.x cc)
           (m/norm (.y cc) -151.90758 151.90758 0.0 255.0)
           (m/norm (.z cc) -133.260705 133.260705 0.0 255.0)
           (.w cc))))

(defn from-YIQ-raw
  "YIQ -> RGB"
  [^Vec4 c]
  (let [Y (.x c)
        I (.y c)
        Q (.z c)
        r (+ Y (* +0.9562957197589482261 I) (* 0.6210244164652610754 Q))
        g (+ Y (* -0.2721220993185104464 I) (* -0.6473805968256950427 Q))
        b (+ Y (* -1.1069890167364901945 I) (* 1.7046149983646481374 Q))]
    (Vec4. r g b (.w c))))

(defn from-YIQ
  "YIQ -> RGB, normalized"
  [^Vec4 c]
  (let [I (m/norm (.y c) 0.0 255.0 -151.90758 151.90758)
        Q (m/norm (.z c) 0.0 255.0 -133.260705 133.260705)]
    (from-YIQ-raw (Vec4. (.x c) I Q (.w c)))))

;; ### YCgCo

(defn to-YCgCo-raw
  "RGB -> YCgCo

  Output range:
  
  * Y - 0.0 - 255.0
  * Cg - -127.5 - 127.5
  * Co - -127.5 - 127.5"
  [^Vec4 c]
  (let [Y (+ (* 0.25 (.x c)) (* 0.5 (.y c)) (* 0.25 (.z c)))
        Cg (+ (* -0.25 (.x c)) (* 0.5 (.y c)) (* -0.25 (.z c)))
        Co (+ (* 0.5 (.x c)) (* -0.5 (.z c)))]
    (Vec4. Y Cg Co (.w c))))

(defn to-YCgCo
  "RGB -> YCgCo, normalized"
  [c]
  (v/add (to-YCgCo-raw c) y-norm))

(defn from-YCgCo-raw
  "YCgCo -> RGB"
  [^Vec4 c]
  (let [Cg (.y c)
        Co (.z c)
        tmp (- (.x c) Cg)]
    (Vec4. (+ Co tmp) (+ (.x c) Cg) (- tmp Co) (.w c))))

(defn from-YCgCo
  "YCgCo -> RGB, normalized"
  [c]
  (from-YCgCo-raw (v/sub c y-norm)))

;; Cubehelix

(def ^:private ^:const ^:double ch-a -0.14861)
(def ^:private ^:const ^:double ch-b 1.78277)
(def ^:private ^:const ^:double ch-c -0.29227)
(def ^:private ^:const ^:double ch-d -0.90649)
(def ^:private ^:const ^:double ch-e 1.97294)
(def ^:private ^:const ^:double ch-ed (* ch-e ch-d))
(def ^:private ^:const ^:double ch-eb (* ch-e ch-b))
(def ^:private ^:const ^:double ch-bc-da (- (* ch-b ch-c) (* ch-d ch-a)))
(def ^:private ^:const ^:double ch-bc-da+ed-eb-r (/ (+ ch-bc-da ch-ed (- ch-eb))))

(defn to-Cubehelix-raw
  "RGB -> Cubehelix

  Output range:

  * h - 0.0 - 360.0
  * s - 0.0 - 4.614
  * l - 0.0 - 1.0"
  [^Vec4 c]
  (let [r (/ (.x c) 255.0)
        g (/ (.y c) 255.0)
        b (/ (.z c) 255.0)
        l (* ch-bc-da+ed-eb-r (+ (* ch-bc-da b) (* ch-ed r) (- (* ch-eb g))))
        bl (- b l)
        k (/ (- (* ch-e (- g l)) (* ch-c bl)) ch-d)
        s (/ (m/sqrt (+ (* k k) (* bl bl)))
             (* ch-e l (- 1.0 l)))]
    (if (Double/isNaN s)
      (Vec4. 0.0 0.0 l (.w c))
      (let [h (- (* (m/atan2 k bl) m/rad-in-deg) 120.0)]
        (Vec4. (if (neg? h) (+ h 360.0) h) s l (.w c))))))

(defn to-Cubehelix
  "RGB -> Cubehelix, normalized"
  [^Vec4 c]
  (let [^Vec4 cc (to-Cubehelix-raw c)]
    (Vec4. (m/norm (.x cc) 0.0 359.9932808311505 0.0 255.0)
           (m/norm (.y cc) 0.0 4.61438686803972 0.0 255.0)
           (* 255.0 (.z cc))
           (.w c))))

(defn from-Cubehelix-raw
  "Cubehelix -> RGB"
  [^Vec4 c]
  (let [h (* (+ (.x c) 120.0) m/deg-in-rad)
        l (.z c)
        a (* (.y c) l (- 1.0 l))
        cosh (m/cos h)
        sinh (m/sin h)]
    (Vec4. (* 255.0 (+ l (* a (+ (* ch-a cosh) (* ch-b sinh)))))
           (* 255.0 (+ l (* a (+ (* ch-c cosh) (* ch-d sinh)))))
           (* 255.0 (+ l (* a ch-e cosh)))
           (.w c))))

(defn from-Cubehelix
  "Cubehelix -> RGB, normalized"
  [^Vec4 c]
  (let [cc (Vec4. (m/norm (.x c) 0.0 255.0 0.0 359.9932808311505)
                  (m/norm (.y c) 0.0 255.0 0.0 4.61438686803972)
                  (/ (.z c) 255.0)
                  (.w c))]
    (from-Cubehelix-raw cc)))

;; ### Grayscale

(defn to-Gray
  "RGB->Grayscale"
  [^Vec4 c]
  (let [^double l (luma c)]
    (Vec4. l l l (.w c))))

;; do nothing in reverse
(def from-Gray identity)

;; Just for a case "do nothing"
(def to-RGB identity)
(def from-RGB identity)

;; List of all color spaces with functions
(def colorspaces {:CMY   [to-CMY from-CMY]
                  :OHTA  [to-OHTA from-OHTA]
                  :XYZ   [to-XYZ from-XYZ]
                  :YXY   [to-YXY from-YXY]
                  :LUV   [to-LUV from-LUV]
                  :LAB   [to-LAB from-LAB]
                  :HCL   [to-HCL from-HCL]
                  :HSB   [to-HSB from-HSB]
                  :HSI   [to-HSI from-HSI]
                  :HSL   [to-HSL from-HSL]
                  :HSV   [to-HSV from-HSV]
                  :HWB   [to-HWB from-HWB]
                  :GLHS  [to-GLHS from-GLHS]
                  :YPbPr [to-YPbPr from-YPbPr]
                  :YDbDr [to-YDbDr from-YDbDr]
                  :YCbCr [to-YCbCr from-YCbCr]
                  :YCgCo [to-YCgCo from-YCgCo]
                  :YUV   [to-YUV from-YUV]
                  :YIQ   [to-YIQ from-YIQ]
                  :Gray  [to-Gray from-Gray]
                  :sRGB  [to-sRGB from-sRGB]
                  :Cubehelix [to-Cubehelix from-Cubehelix]
                  :RGB   [identity identity]})

;; List of color spaces names
(def colorspaces-names (keys colorspaces))

(defn to-cs
  "Return colorspace converter by keyword (RGB -> ...)"
  [cs]
  ((cs colorspaces) 0))

(defn from-cs
  "Return colorspace converter by keyword (... -> RGB)"
  [cs]
  ((cs colorspaces) 1))

(defn make-color-converter
  "Create fn which converts provided color and scale values from provided range (to simulate Processing `colorMode` fn)"
  ([colorspace-fn ch1-scale ch2-scale ch3-scale ch4-scale]
   (fn [^Vec4 v]
     (let [ch1 (* 255.0 (/ (.x v) ^double ch1-scale))
           ch2 (* 255.0 (/ (.y v) ^double ch2-scale))
           ch3 (* 255.0 (/ (.z v) ^double ch3-scale))
           ch4 (* 255.0 (/ (.w v) ^double ch4-scale))]
       (colorspace-fn (v/applyf (Vec4. ch1 ch2 ch3 ch4) clamp255)))))
  ([colorspace-fn ch1-scale ch2-scale ch3-scale] (make-color-converter colorspace-fn ch1-scale ch2-scale ch3-scale 255.0))
  ([colorspace-fn ch-scale] (make-color-converter colorspace-fn ch-scale ch-scale ch-scale ch-scale))
  ([colorspace-fn] colorspace-fn))

;; ## Palettes

;; Gradient function

(defn gradient
  "Create gradient function from palette (list of colors).

  Grandient function accepts value from 0 to 1 and returns interpolated color.

  Optionally interpolate in given `colorspace` and `interpolator` name as keyword. See fastmath [interpolator names](https://generateme.github.io/fastmath/fastmath.interpolation.html#var-interpolators-list)."
  ([palette] (gradient palette :RGB :linear))
  ([palette colorspace] (gradient palette colorspace :linear)) 
  ([palette colorspace interpolator]
   (let [[to from] (colorspaces colorspace)
         cpalette (->> palette
                       (map to-color)
                       (map to))
         r (map #(m/norm % 0.0 (dec (count palette)) 0.0 1.0) (range (count palette)))
         c0 (map ch0 cpalette)
         c1 (map ch1 cpalette)
         c2 (map ch2 cpalette)
         c3 (map alpha cpalette)
         i0 ((i/interpolators-list interpolator) r c0)
         i1 ((i/interpolators-list interpolator) r c1)
         i2 ((i/interpolators-list interpolator) r c2)
         i3 ((i/interpolators-list interpolator) r c3)]
     (fn [^double t]
       (let [ct (m/constrain t 0.0 1.0)]
         (from (v/vec4 (i0 ct) (i1 ct) (i2 ct) (i3 ct))))))))

;; ### Colourlovers

;; Read and parse 500 best palettes taken from http://www.colourlovers.com/ (stored locally)
(def colourlovers-palettes
  (let [f (fn [xml-in] (map (fn [x] (map #((:content %) 0) (:content (first (filter #(= (:tag %) :colors) (:content ((:content xml-in) x))))))) (range 100)))
        all (->> (range 5)
                 (map clojure.core/inc)
                 (map #(str "cl" % ".xml.gz"))
                 (map resource)
                 (map input-stream)
                 (map #(java.util.zip.GZIPInputStream. %))
                 (map xml/parse)                 
                 (map f)
                 (apply concat))]
    (mapv (fn [x] (map to-color x)) all)))

;; ### Inigo Quilez

;; http://iquilezles.org/www/articles/palettes/palettes.htm

(defn iq-palette-gradient
  "Create palette generator function with given parametrization"
  [a b c d]
  (fn [t]
    (let [^Vec3 cc (-> (->> t
                            (v/mult c)
                            (v/add d))
                       (v/mult m/TWO_PI)
                       (v/applyf #(m/cos %))
                       (v/emult b)
                       (v/add a))]
      (-> (Vec4. (.x cc) (.y cc) (.z cc) 1.0)
          (v/mult 255.0)
          (v/applyf clamp255)))))

(defn iq-palette-random-gradient
  "Create random iq palette."
  []
  (let [a (v/generate-vec3 (partial r/drand 0.2 0.8))
        b (v/generate-vec3 (partial r/drand 0.2 0.8))
        c (v/generate-vec3 (partial r/drand 2))
        d (v/generate-vec3 r/drand)]
    (iq-palette-gradient a b c d)))

;; ### Paletton

;; Here you can find reimplementation of http://paletton.com palette generator.
;; You can create palette based on `hue` value with following options:
;;
;; * Palette type, one from `:monochromatic`, `:triad`, `:tetrad`
;; * Paletton presets, check `paletton-presets-names` value
;; * Complementary color
;; * Angle between colors for `:triad` and `:tetrad`

(def paletton-base-data
  (let [s (fn ^double [^double e ^double t ^double n] (if (== n -1.0) e
                                                          (+ e (/ (- t e) (inc n)))))
        i (fn ^double [^double e ^double t ^double n] (if (== n -1.0) t
                                                          (+ t (/ (- e t) (inc n)))))
        paletton-base-values   {:r  [1.0 1.0]
                                :rg [1.0 1.0]
                                :g  [1.0 0.8]
                                :gb [1.0 0.6]
                                :b  [0.85 0.7]
                                :br [1.0 0.65]}]
    {120.0 {:a (:r paletton-base-values)
            :b (:rg paletton-base-values)
            :f (fn ^double [^double e]
                 (if (== e 0.0) -1.0
                     (* 0.5 (m/tan (* m/HALF_PI (/ (- 120.0 e) 120.0))))))
            :fi (fn ^double [^double e]
                  (if (== e -1.0) 0.0
                      (- 120.0 (* 2.0 (/ (* (m/atan (/ e 0.5)) 120.0) m/PI)))))
            :g s
            :rgb (fn [e n r] (Vec4. e n r 255.0))}
     180.0 {:a (:rg paletton-base-values)
            :b (:g paletton-base-values)
            :f (fn ^double [^double e]
                 (if (== e 180.0) -1.0
                     (* 0.5 (m/tan (* m/HALF_PI (/ (- e 120.0) 60.0))))))
            :fi (fn ^double [^double e]
                  (if (== e -1.0) 180.0
                      (+ 120.0 (* 2.0 (/ (* (m/atan (/ e 0.5)) 60.0) m/PI)))))
            :g i
            :rgb (fn [e n r] (Vec4. n e r 255.0))}
     
     210.0 {:a (:g paletton-base-values)
            :b (:gb paletton-base-values)
            :f (fn ^double [^double e]
                 (if (== e 180.0) -1.0
                     (* 0.75 (m/tan (* m/HALF_PI (/ (- 210.0 e) 30.0))))))
            :fi (fn ^double [^double e]
                  (if (== e -1.0) 180.0
                      (- 210.0 (* 2.0 (/ (* (m/atan (/ e 0.75)) 30.0) m/PI)))))
            :g s
            :rgb (fn [e n r] (Vec4. r e n 255.0))}
     255.0 {:a (:gb paletton-base-values)
            :b (:b paletton-base-values)
            :f (fn ^double [^double e]
                 (if (== e 255.0) -1.0
                     (* 1.33 (m/tan (* m/HALF_PI (/ (- e 210.0) 45.0))))))
            :fi (fn ^double [^double e]
                  (if (== e -1.0) 255.0
                      (+ 210.0 (* 2.0 (/ (* (m/atan (/ e 1.33)) 45.0) m/PI)))))
            :g i
            :rgb (fn [e n r] (Vec4. r n e 255.0))}
     
     315.0 {:a (:b paletton-base-values)
            :b (:br paletton-base-values)
            :f (fn ^double [^double e]
                 (if (== e 255.0) -1.0
                     (* 1.33 (m/tan (* m/HALF_PI (/ (- 315.0 e) 60.0))))))
            :fi (fn ^double [^double e]
                  (if (== e -1.0) 255.0
                      (- 315.0 (* 2.0 (/ (* (m/atan (/ e 1.33)) 60.0) m/PI)))))
            :g s
            :rgb (fn [e n r] (Vec4. n r e 255.0))}
     360.0 {:a (:br paletton-base-values)
            :b (:r paletton-base-values)
            :f (fn ^double [^double e]
                 (if (== e 0.0) -1.0
                     (* 1.33 (m/tan (* m/HALF_PI (/ (- e 315.0) 45.0))))))
            :fi (fn ^double [^double e]
                  (if (== e -1.0) 0.0
                      (+ 315.0 (* 2.0 (/ (* (m/atan (/ e 1.33)) 45.0) m/PI)))))
            :g i
            :rgb (fn [e n r] (Vec4. e r n 255.0))}}))

(defn paletton-hsv-to-rgb
  "Paletton version of HSV to RGB converter"
  [^double hue ^double ks ^double kv]
  (let [ks (m/constrain ks 0.0 2.0)
        kv (m/constrain kv 0.0 2.0)
        h (mod hue 360.0)
        upd (fn ^double [^double e ^double t] (if (<= t 1.0)
                                                (* e t)
                                                (+ e (* (- 1.0 e) (dec t)))))
        {:keys [a b f g rgb]} (second (first (filter #(< h ^double (% 0)) paletton-base-data)))
        av (second a)
        bv (second b)
        as (first a)
        bs (first b)
        ^double n (f h)
        ^double v (upd (g av bv n) kv)
        ^double s (upd (g as bs n) ks)
        r (* 255.0 v)
        b (* r (- 1.0 s))
        g (if (== n -1.0) b
              (/ (+ r (* n b)) (inc n)))]
    (rgb r g b)))

(defn paletton-rgb-to-hue
  "Take paletton HUE from RGB"
  (^double [^double r ^double g ^double b]
   (if (== r g b)
     0.0
     (let [f (max r g b)
           p (min r g b)
           [^double l ^double i] (if (== f r)
                                   (if (== p b)
                                     [g (:fi (paletton-base-data 120.0))]
                                     [b (:fi (paletton-base-data 360.0))])
                                   (if (== f g)
                                     (if (== p r)
                                       [b (:fi (paletton-base-data 210.0))]
                                       [r (:fi (paletton-base-data 180.0))])
                                     (if (== p r)
                                       [g (:fi (paletton-base-data 255.0))]
                                       [r (:fi (paletton-base-data 315.0))])))
                                        ;d (/ (- f p) f) ;; saturation
                                        ;v (/ f 255.0)   ;; value
           s (i (if (== l p) -1.0
                    (/ (- f l) (- l p))))]
       s)))
  ([^Vec4 c] (paletton-rgb-to-hue (.x c) (.y c) (.z c))))

;; List of paletton presets
(def paletton-presets
  {:pale-light          [[0.24649 1.78676] [0.09956 1.95603] [0.17209 1.88583] [0.32122 1.65929] [0.39549 1.50186]]
   :pastels-bright      [[0.65667 1.86024] [0.04738 1.99142] [0.39536 1.89478] [0.90297 1.85419] [1.86422 1.8314]]
   :shiny               [[1.00926 2] [0.3587 2] [0.5609 2] [2 0.8502] [2 0.65438]]
   :pastels-lightest    [[0.34088 1.09786] [0.13417 1.62645] [0.23137 1.38072] [0.45993 0.92696] [0.58431 0.81098]]
   :pastels-very-light  [[0.58181 1.32382] [0.27125 1.81913] [0.44103 1.59111] [0.70192 1.02722] [0.84207 0.91425]]
   :full                [[1 1] [0.61056 1.24992] [0.77653 1.05996] [1.06489 0.77234] [1.25783 0.60685]]
   :pastels-light       [[0.37045 0.90707] [0.15557 1.28367] [0.25644 1.00735] [0.49686 0.809] [0.64701 0.69855]]
   :pastels-med         [[0.66333 0.8267] [0.36107 1.30435] [0.52846 0.95991] [0.78722 0.70882] [0.91265 0.5616]]
   :darker              [[0.93741 0.68672] [0.68147 0.88956] [0.86714 0.82989] [1.12072 0.5673] [1.44641 0.42034]]
   :pastels-mid-pale    [[0.38302 0.68001] [0.15521 0.98457] [0.26994 0.81586] [0.46705 0.54194] [0.64065 0.44875]]
   :pastels             [[0.66667 0.66667] [0.33333 1] [0.5 0.83333] [0.83333 0.5] [1 0.33333]]
   :dark-neon           [[0.94645 0.59068] [0.99347 0.91968] [0.93954 0.7292] [1.01481 0.41313] [1.04535 0.24368]]
   :pastels-dark        [[0.36687 0.39819] [0.25044 0.65561] [0.319 0.54623] [0.55984 0.37953] [0.70913 0.3436]]
   :pastels-very-dark   [[0.60117 0.41845] [0.36899 0.59144] [0.42329 0.44436] [0.72826 0.35958] [0.88393 0.27004]]
   :dark                [[1.31883 0.40212] [0.9768 0.25402] [1.27265 0.30941] [1.21289 0.60821] [1.29837 0.82751]]
   :pastels-mid-dark    [[0.26952 0.22044] [0.23405 0.52735] [0.23104 0.37616] [0.42324 0.20502] [0.54424 0.18483]]
   :pastels-darkest     [[0.53019 0.23973] [0.48102 0.50306] [0.50001 0.36755] [0.6643 0.32778] [0.77714 0.3761]]
   :darkest             [[1.46455 0.21042] [0.99797 0.16373] [0.96326 0.274] [1.56924 0.45022] [1.23016 0.66]]
   :almost-black        [[0.12194 0.15399] [0.34224 0.50742] [0.24211 0.34429] [0.31846 0.24986] [0.52251 0.33869]]
   :almost-gray-dark    [[0.10266 0.24053] [0.13577 0.39387] [0.11716 0.30603] [0.14993 0.22462] [0.29809 0.19255]]
   :almost-gray-darker  [[0.07336 0.36815] [0.18061 0.50026] [0.09777 0.314] [0.12238 0.25831] [0.14388 0.1883]]
   :almost-gray-mid     [[0.07291 0.59958] [0.19602 0.74092] [0.10876 0.5366] [0.15632 0.48229] [0.20323 0.42268]]
   :almost-gray-lighter [[0.06074 0.82834] [0.14546 0.97794] [0.10798 0.76459] [0.15939 0.68697] [0.22171 0.62926]]
   :almost-gray-light   [[0.03501 1.59439] [0.23204 1.10483] [0.14935 1.33784] [0.07371 1.04897] [0.09635 0.91368]]})

;; List of preset names
(def paletton-presets-names (keys paletton-presets))

(defn make-monochromatic-palette
  "Create monochromatic palette from hue and preset."
  [hue preset]
  (mapv (fn [[ks kv]] (paletton-hsv-to-rgb hue ks kv)) preset))

(defmulti paletton-palette (fn [m hue & conf] m))

;; Following methods can be used to create paletton palettes.
;; As a dispatch use one of the types `:monochromatic`, `:triad` or `:tetrad`.
;; Parameters are `hue` value and configuration.

;; `:monochromatic` configuration
;;
;; * `:compl` - use complementary color? (true/false)
;; * `:preset` - what preset to use (one from `paletton-preset-names`)
(defmethod paletton-palette :monochromatic [_ hue & conf]
  (let [{compl :compl 
         preset :preset
         :or {compl false
              preset :full}} (first conf)
        ppreset (if (keyword? preset) (paletton-presets preset) preset)
        p (make-monochromatic-palette hue ppreset)]
    (if compl (vec (concat p (make-monochromatic-palette (+ ^double hue 180.0) ppreset))) p)))

;; `:triad` configuration
;;
;; * `:compl` - use complementary color? (true/false)
;; * `:preset` - what preset to use (one from `paletton-preset-names`)
;; * `:angle` - angle between main hue and two additional
;; * `:adj` - use adjacent version of triad (true/false)
(defmethod paletton-palette :triad [_ hue & conf]
  (let [{compl :compl
         preset :preset
         ^double angle :angle
         adj :adj
         :or {compl false
              preset :full
              angle 30.0
              adj true}} (first conf)
        chue (+ 180.0 ^double hue)
        hue1 (if adj (+ ^double hue angle) (+ chue angle))
        hue2 (if adj (- ^double hue angle) (- chue angle))
        ppreset (if (keyword? preset) (paletton-presets preset) preset)
        p1 (make-monochromatic-palette hue ppreset)
        p2 (make-monochromatic-palette hue1 ppreset)
        p3 (make-monochromatic-palette hue2 ppreset)
        p (vec (concat p1 p2 p3))]
    (if compl (vec (concat p (make-monochromatic-palette chue ppreset))) p)))

;; `:tetrad` configuration
;;
;; * `:preset` - what preset to use (one from `paletton-preset-names`)
;; * `:angle` - angle between main hue and additional color
(defmethod paletton-palette :tetrad [_ hue & conf]
  (let [{preset :preset
         ^double angle :angle
         :or {preset :full
              angle 30.0}} (first conf)
        p1 (paletton-palette :monochromatic hue {:preset preset :compl true})
        p2 (paletton-palette :monochromatic (+ angle ^double hue) {:preset preset :compl true})]
    (vec (concat p1 p2))))

;; ## Additional functions

(defn nearest-color
  "Find nearest color from a set. Input: distance function (default euclidean), list of target colors and source color."
  ([f xf c]
   (let [s (count xf)]
     (loop [i (int 0)
            currc c
            currdist Double/MAX_VALUE]
       (if (< i s)
         (let [c1 (xf i)
               dist (double (f c c1))]
           (recur (unchecked-inc i)
                  (if (< dist currdist) c1 currc)
                  (if (< dist currdist) dist currdist)))
         currc))))
  ([xf c]
   (nearest-color v/dist xf c)))

(defn make-reduce-color-filter
  "Define reduce color filter to use on `Pixels`."
  ([pal]
   (partial nearest-color pal))
  ([f pal]
   (partial nearest-color f pal)))

(def html-colors-map {:aliceblue 0xf0f8ff,
                      :antiquewhite 0xfaebd7,
                      :amber (color 178 140 0)
                      :aqua 0x00ffff,
                      :aquamarine 0x7fffd4,
                      :azure 0xf0ffff,
                      :beige 0xf5f5dc,
                      :bisque 0xffe4c4,
                      :black 0x000000,
                      :blanchedalmond 0xffebcd,
                      :blue 0x0000ff,
                      :blueviolet 0x8a2be2,
                      :brown 0xa52a2a,
                      :burlywood 0xdeb887,
                      :cadetblue 0x5f9ea0,
                      :chartreuse 0x7fff00,
                      :chocolate 0xd2691e,
                      :coral 0xff7f50,
                      :cornflowerblue 0x6495ed,
                      :cornsilk 0xfff8dc,
                      :crimson 0xdc143c,
                      :cyan 0x00ffff,
                      :darkblue 0x00008b,
                      :darkcyan 0x008b8b,
                      :darkgoldenrod 0xb8860b,
                      :darkgray 0xa9a9a9,
                      :darkgreen 0x006400,
                      :darkgrey 0xa9a9a9,
                      :darkkhaki 0xbdb76b,
                      :darkmagenta 0x8b008b,
                      :darkolivegreen 0x556b2f,
                      :darkorange 0xff8c00,
                      :darkorchid 0x9932cc,
                      :darkred 0x8b0000,
                      :darksalmon 0xe9967a,
                      :darkseagreen 0x8fbc8f,
                      :darkslateblue 0x483d8b,
                      :darkslategray 0x2f4f4f,
                      :darkslategrey 0x2f4f4f,
                      :darkturquoise 0x00ced1,
                      :darkviolet 0x9400d3,
                      :deeppink 0xff1493,
                      :deepskyblue 0x00bfff,
                      :dimgray 0x696969,
                      :dimgrey 0x696969,
                      :dodgerblue 0x1e90ff,
                      :firebrick 0xb22222,
                      :floralwhite 0xfffaf0,
                      :forestgreen 0x228b22,
                      :fuchsia 0xff00ff,
                      :gainsboro 0xdcdcdc,
                      :ghostwhite 0xf8f8ff,
                      :gold 0xffd700,
                      :goldenrod 0xdaa520,
                      :gray 0x808080,
                      :green 0x008000,
                      :greenyellow 0xadff2f,
                      :grey 0x808080,
                      :honeydew 0xf0fff0,
                      :hotpink 0xff69b4,
                      :indianred 0xcd5c5c,
                      :indigo 0x4b0082,
                      :ivory 0xfffff0,
                      :khaki 0xf0e68c,
                      :lavender 0xe6e6fa,
                      :lavenderblush 0xfff0f5,
                      :lawngreen 0x7cfc00,
                      :lemonchiffon 0xfffacd,
                      :lightblue 0xadd8e6,
                      :lightcoral 0xf08080,
                      :lightcyan 0xe0ffff,
                      :lightgoldenrodyellow 0xfafad2,
                      :lightgray 0xd3d3d3,
                      :lightgreen 0x90ee90,
                      :lightgrey 0xd3d3d3,
                      :lightpink 0xffb6c1,
                      :lightsalmon 0xffa07a,
                      :lightseagreen 0x20b2aa,
                      :lightskyblue 0x87cefa,
                      :lightslategray 0x778899,
                      :lightslategrey 0x778899,
                      :lightsteelblue 0xb0c4de,
                      :lightyellow 0xffffe0,
                      :lime 0x00ff00,
                      :limegreen 0x32cd32,
                      :linen 0xfaf0e6,
                      :magenta 0xff00ff,
                      :maroon 0x800000,
                      :mediumaquamarine 0x66cdaa,
                      :mediumblue 0x0000cd,
                      :mediumorchid 0xba55d3,
                      :mediumpurple 0x9370db,
                      :mediumseagreen 0x3cb371,
                      :mediumslateblue 0x7b68ee,
                      :mediumspringgreen 0x00fa9a,
                      :mediumturquoise 0x48d1cc,
                      :mediumvioletred 0xc71585,
                      :midnightblue 0x191970,
                      :mintcream 0xf5fffa,
                      :mistyrose 0xffe4e1,
                      :moccasin 0xffe4b5,
                      :navajowhite 0xffdead,
                      :navy 0x000080,
                      :oldlace 0xfdf5e6,
                      :olive 0x808000,
                      :olivedrab 0x6b8e23,
                      :orange 0xffa500,
                      :orangered 0xff4500,
                      :orchid 0xda70d6,
                      :palegoldenrod 0xeee8aa,
                      :palegreen 0x98fb98,
                      :paleturquoise 0xafeeee,
                      :palevioletred 0xdb7093,
                      :papayawhip 0xffefd5,
                      :peachpuff 0xffdab9,
                      :peru 0xcd853f,
                      :pink 0xffc0cb,
                      :plum 0xdda0dd,
                      :powderblue 0xb0e0e6,
                      :purple 0x800080,
                      :rebeccapurple 0x663399,
                      :red 0xff0000,
                      :rosybrown 0xbc8f8f,
                      :royalblue 0x4169e1,
                      :saddlebrown 0x8b4513,
                      :salmon 0xfa8072,
                      :sandybrown 0xf4a460,
                      :seagreen 0x2e8b57,
                      :seashell 0xfff5ee,
                      :sienna 0xa0522d,
                      :silver 0xc0c0c0,
                      :skyblue 0x87ceeb,
                      :slateblue 0x6a5acd,
                      :slategray 0x708090,
                      :slategrey 0x708090,
                      :snow 0xfffafa,
                      :springgreen 0x00ff7f,
                      :steelblue 0x4682b4,
                      :tan 0xd2b48c,
                      :teal 0x008080,
                      :thistle 0xd8bfd8,
                      :tomato 0xff6347,
                      :turquoise 0x40e0d0,
                      :violet 0xee82ee,
                      :wheat 0xf5deb3,
                      :white 0xffffff,
                      :whitesmoke 0xf5f5f5,
                      :yellow 0xffff00,
                      :yellowgreen 0x9acd32})

(def html-awt-color (memoize (comp to-awt-color html-colors-map)))
(def html-color (memoize (comp to-color html-colors-map)))

;;

(defn- d3->palette
  "Convert d3 string to palette"
  [s]
  (mapv to-color (map clojure.string/join (partition 6 s))))

(defn- d3->palettes
  "Convert list of palettes into map."
  [name xs]
  (let [pals (map d3->palette xs)
        names (map #(keyword (str name "-" (count %))) pals)]
    (into {} (map vector names pals))))

(def ^{:doc "D3 color presets."} scale-chromatic
  (merge
   (d3->palettes "brbg" ["d8b365f5f5f55ab4ac",
                         "a6611adfc27d80cdc1018571",
                         "a6611adfc27df5f5f580cdc1018571",
                         "8c510ad8b365f6e8c3c7eae55ab4ac01665e",
                         "8c510ad8b365f6e8c3f5f5f5c7eae55ab4ac01665e",
                         "8c510abf812ddfc27df6e8c3c7eae580cdc135978f01665e",
                         "8c510abf812ddfc27df6e8c3f5f5f5c7eae580cdc135978f01665e",
                         "5430058c510abf812ddfc27df6e8c3c7eae580cdc135978f01665e003c30",
                         "5430058c510abf812ddfc27df6e8c3f5f5f5c7eae580cdc135978f01665e003c30"])
   (d3->palettes "prgn" ["af8dc3f7f7f77fbf7b",
                         "7b3294c2a5cfa6dba0008837",
                         "7b3294c2a5cff7f7f7a6dba0008837",
                         "762a83af8dc3e7d4e8d9f0d37fbf7b1b7837",
                         "762a83af8dc3e7d4e8f7f7f7d9f0d37fbf7b1b7837",
                         "762a839970abc2a5cfe7d4e8d9f0d3a6dba05aae611b7837",
                         "762a839970abc2a5cfe7d4e8f7f7f7d9f0d3a6dba05aae611b7837",
                         "40004b762a839970abc2a5cfe7d4e8d9f0d3a6dba05aae611b783700441b",
                         "40004b762a839970abc2a5cfe7d4e8f7f7f7d9f0d3a6dba05aae611b783700441b"])
   
   {:accent (d3->palette "7fc97fbeaed4fdc086ffff99386cb0f0027fbf5b17666666")
    :dark2 (d3->palette "1b9e77d95f027570b3e7298a66a61ee6ab02a6761d666666")
    :paired (d3->palette "a6cee31f78b4b2df8a33a02cfb9a99e31a1cfdbf6fff7f00cab2d66a3d9affff99b15928")
    :pastel1 (d3->palette "fbb4aeb3cde3ccebc5decbe4fed9a6ffffcce5d8bdfddaecf2f2f2")
    :pastel2 (d3->palette "b3e2cdfdcdaccbd5e8f4cae4e6f5c9fff2aef1e2cccccccc")
    :set1 (d3->palette "e41a1c377eb84daf4a984ea3ff7f00ffff33a65628f781bf999999")
    :set2 (d3->palette "66c2a5fc8d628da0cbe78ac3a6d854ffd92fe5c494b3b3b3")
    :set3 (d3->palette "8dd3c7ffffb3bebadafb807280b1d3fdb462b3de69fccde5d9d9d9bc80bdccebc5ffed6f")
    :category10 (d3->palette "1f77b4ff7f0e2ca02cd627289467bd8c564be377c27f7f7fbcbd2217becf")

    :category20 (mapv to-color [0x1f77b4 0xaec7e8 0xff7f0e 0xffbb78 0x2ca02c
                                0x98df8a 0xd62728 0xff9896 0x9467bd 0xc5b0d5
                                0x8c564b 0xc49c94 0xe377c2 0xf7b6d2 0x7f7f7f
                                0xc7c7c7 0xbcbd22 0xdbdb8d 0x17becf 0x9edae5])

    :category20b (mapv to-color [0x393b79 0x5254a3 0x6b6ecf 0x9c9ede 0x637939
                                 0x8ca252 0xb5cf6b 0xcedb9c 0x8c6d31 0xbd9e39
                                 0xe7ba52 0xe7cb94 0x843c39 0xad494a 0xd6616b
                                 0xe7969c 0x7b4173 0xa55194 0xce6dbd 0xde9ed6])

    :category20c (mapv to-color [0x3182bd 0x6baed6 0x9ecae1 0xc6dbef 0xe6550d
                                 0xfd8d3c 0xfdae6b 0xfdd0a2 0x31a354 0x74c476
                                 0xa1d99b 0xc7e9c0 0x756bb1 0x9e9ac8 0xbcbddc
                                 0xdadaeb 0x636363 0x969696 0xbdbdbd 0xd9d9d9])

    :viridis (d3->palette "44015444025645045745055946075a46085c460a5d460b5e470d60470e6147106347116447136548146748166848176948186a481a6c481b6d481c6e481d6f481f70482071482173482374482475482576482677482878482979472a7a472c7a472d7b472e7c472f7d46307e46327e46337f463480453581453781453882443983443a83443b84433d84433e85423f854240864241864142874144874045884046883f47883f48893e49893e4a893e4c8a3d4d8a3d4e8a3c4f8a3c508b3b518b3b528b3a538b3a548c39558c39568c38588c38598c375a8c375b8d365c8d365d8d355e8d355f8d34608d34618d33628d33638d32648e32658e31668e31678e31688e30698e306a8e2f6b8e2f6c8e2e6d8e2e6e8e2e6f8e2d708e2d718e2c718e2c728e2c738e2b748e2b758e2a768e2a778e2a788e29798e297a8e297b8e287c8e287d8e277e8e277f8e27808e26818e26828e26828e25838e25848e25858e24868e24878e23888e23898e238a8d228b8d228c8d228d8d218e8d218f8d21908d21918c20928c20928c20938c1f948c1f958b1f968b1f978b1f988b1f998a1f9a8a1e9b8a1e9c891e9d891f9e891f9f881fa0881fa1881fa1871fa28720a38620a48621a58521a68522a78522a88423a98324aa8325ab8225ac8226ad8127ad8128ae8029af7f2ab07f2cb17e2db27d2eb37c2fb47c31b57b32b67a34b67935b77937b87838b9773aba763bbb753dbc743fbc7340bd7242be7144bf7046c06f48c16e4ac16d4cc26c4ec36b50c46a52c56954c56856c66758c7655ac8645cc8635ec96260ca6063cb5f65cb5e67cc5c69cd5b6ccd5a6ece5870cf5773d05675d05477d1537ad1517cd2507fd34e81d34d84d44b86d54989d5488bd6468ed64590d74393d74195d84098d83e9bd93c9dd93ba0da39a2da37a5db36a8db34aadc32addc30b0dd2fb2dd2db5de2bb8de29bade28bddf26c0df25c2df23c5e021c8e020cae11fcde11dd0e11cd2e21bd5e21ad8e219dae319dde318dfe318e2e418e5e419e7e419eae51aece51befe51cf1e51df4e61ef6e620f8e621fbe723fde725")
    :viridis-magma (d3->palette "00000401000501010601010802010902020b02020d03030f03031204041405041606051806051a07061c08071e0907200a08220b09240c09260d0a290e0b2b100b2d110c2f120d31130d34140e36150e38160f3b180f3d19103f1a10421c10441d11471e114920114b21114e22115024125325125527125829115a2a115c2c115f2d11612f116331116533106734106936106b38106c390f6e3b0f703d0f713f0f72400f74420f75440f764510774710784910784a10794c117a4e117b4f127b51127c52137c54137d56147d57157e59157e5a167e5c167f5d177f5f187f601880621980641a80651a80671b80681c816a1c816b1d816d1d816e1e81701f81721f817320817521817621817822817922827b23827c23827e24828025828125818326818426818627818827818928818b29818c29818e2a81902a81912b81932b80942c80962c80982d80992d809b2e7f9c2e7f9e2f7fa02f7fa1307ea3307ea5317ea6317da8327daa337dab337cad347cae347bb0357bb2357bb3367ab5367ab73779b83779ba3878bc3978bd3977bf3a77c03a76c23b75c43c75c53c74c73d73c83e73ca3e72cc3f71cd4071cf4070d0416fd2426fd3436ed5446dd6456cd8456cd9466bdb476adc4869de4968df4a68e04c67e24d66e34e65e44f64e55064e75263e85362e95462ea5661eb5760ec5860ed5a5fee5b5eef5d5ef05f5ef1605df2625df2645cf3655cf4675cf4695cf56b5cf66c5cf66e5cf7705cf7725cf8745cf8765cf9785df9795df97b5dfa7d5efa7f5efa815ffb835ffb8560fb8761fc8961fc8a62fc8c63fc8e64fc9065fd9266fd9467fd9668fd9869fd9a6afd9b6bfe9d6cfe9f6dfea16efea36ffea571fea772fea973feaa74feac76feae77feb078feb27afeb47bfeb67cfeb77efeb97ffebb81febd82febf84fec185fec287fec488fec68afec88cfeca8dfecc8ffecd90fecf92fed194fed395fed597fed799fed89afdda9cfddc9efddea0fde0a1fde2a3fde3a5fde5a7fde7a9fde9aafdebacfcecaefceeb0fcf0b2fcf2b4fcf4b6fcf6b8fcf7b9fcf9bbfcfbbdfcfdbf")
    :viridis-inferno (d3->palette "00000401000501010601010802010a02020c02020e03021004031204031405041706041907051b08051d09061f0a07220b07240c08260d08290e092b10092d110a30120a32140b34150b37160b39180c3c190c3e1b0c411c0c431e0c451f0c48210c4a230c4c240c4f260c51280b53290b552b0b572d0b592f0a5b310a5c320a5e340a5f3609613809623909633b09643d09653e0966400a67420a68440a68450a69470b6a490b6a4a0c6b4c0c6b4d0d6c4f0d6c510e6c520e6d540f6d550f6d57106e59106e5a116e5c126e5d126e5f136e61136e62146e64156e65156e67166e69166e6a176e6c186e6d186e6f196e71196e721a6e741a6e751b6e771c6d781c6d7a1d6d7c1d6d7d1e6d7f1e6c801f6c82206c84206b85216b87216b88226a8a226a8c23698d23698f24699025689225689326679526679727669827669a28659b29649d29649f2a63a02a63a22b62a32c61a52c60a62d60a82e5fa92e5eab2f5ead305dae305cb0315bb1325ab3325ab43359b63458b73557b93556ba3655bc3754bd3853bf3952c03a51c13a50c33b4fc43c4ec63d4dc73e4cc83f4bca404acb4149cc4248ce4347cf4446d04545d24644d34743d44842d54a41d74b3fd84c3ed94d3dda4e3cdb503bdd513ade5238df5337e05536e15635e25734e35933e45a31e55c30e65d2fe75e2ee8602de9612bea632aeb6429eb6628ec6726ed6925ee6a24ef6c23ef6e21f06f20f1711ff1731df2741cf3761bf37819f47918f57b17f57d15f67e14f68013f78212f78410f8850ff8870ef8890cf98b0bf98c0af98e09fa9008fa9207fa9407fb9606fb9706fb9906fb9b06fb9d07fc9f07fca108fca309fca50afca60cfca80dfcaa0ffcac11fcae12fcb014fcb216fcb418fbb61afbb81dfbba1ffbbc21fbbe23fac026fac228fac42afac62df9c72ff9c932f9cb35f8cd37f8cf3af7d13df7d340f6d543f6d746f5d949f5db4cf4dd4ff4df53f4e156f3e35af3e55df2e661f2e865f2ea69f1ec6df1ed71f1ef75f1f179f2f27df2f482f3f586f3f68af4f88ef5f992f6fa96f8fb9af9fc9dfafda1fcffa4")
    :viridis-plasma (d3->palette "0d088710078813078916078a19068c1b068d1d068e20068f2206902406912605912805922a05932c05942e05952f059631059733059735049837049938049a3a049a3c049b3e049c3f049c41049d43039e44039e46039f48039f4903a04b03a14c02a14e02a25002a25102a35302a35502a45601a45801a45901a55b01a55c01a65e01a66001a66100a76300a76400a76600a76700a86900a86a00a86c00a86e00a86f00a87100a87201a87401a87501a87701a87801a87a02a87b02a87d03a87e03a88004a88104a78305a78405a78606a68707a68808a68a09a58b0aa58d0ba58e0ca48f0da4910ea3920fa39410a29511a19613a19814a099159f9a169f9c179e9d189d9e199da01a9ca11b9ba21d9aa31e9aa51f99a62098a72197a82296aa2395ab2494ac2694ad2793ae2892b02991b12a90b22b8fb32c8eb42e8db52f8cb6308bb7318ab83289ba3388bb3488bc3587bd3786be3885bf3984c03a83c13b82c23c81c33d80c43e7fc5407ec6417dc7427cc8437bc9447aca457acb4679cc4778cc4977cd4a76ce4b75cf4c74d04d73d14e72d24f71d35171d45270d5536fd5546ed6556dd7566cd8576bd9586ada5a6ada5b69db5c68dc5d67dd5e66de5f65de6164df6263e06363e16462e26561e26660e3685fe4695ee56a5de56b5de66c5ce76e5be76f5ae87059e97158e97257ea7457eb7556eb7655ec7754ed7953ed7a52ee7b51ef7c51ef7e50f07f4ff0804ef1814df1834cf2844bf3854bf3874af48849f48948f58b47f58c46f68d45f68f44f79044f79143f79342f89441f89540f9973ff9983ef99a3efa9b3dfa9c3cfa9e3bfb9f3afba139fba238fca338fca537fca636fca835fca934fdab33fdac33fdae32fdaf31fdb130fdb22ffdb42ffdb52efeb72dfeb82cfeba2cfebb2bfebd2afebe2afec029fdc229fdc328fdc527fdc627fdc827fdca26fdcb26fccd25fcce25fcd025fcd225fbd324fbd524fbd724fad824fada24f9dc24f9dd25f8df25f8e125f7e225f7e425f6e626f6e826f5e926f5eb27f4ed27f3ee27f3f027f2f227f1f426f1f525f0f724f0f921")}))
