# COVID19 Visualizations in Clojure with Vega

I wanted to better understand COVID-19, so I cloned Johns Hopkins'
daily-updated dataset, fired up a Clojure REPL, and started massaging
the data into a visualization using the Vega grammar. 

This repository is a REPL notebook to **demonstrate** and **extend**
that exploration.


## Usage

A cleaned-up subset of the code I used to produce the visualizations
in the article is in the `article` namespace. If you're new to Clojure
or just want to understand the article, I recommend starting there:

1. Clone the [Johns Hopkins dataset
   repo](https://github.com/CSSEGISandData/COVID-19) to *resources/*
   within this repo
1. Open `appliedsciencestudio.covid19-clj-viz.article`
1. Start a Clojure REPL (based on deps.edn, not Leiningen)
1. Evaluate forms one at a time, with a browser window open next to
   your editor so you can see the visualizatons as you go

Other namespaces are for exploring and visualizing COVID-19 data in other, similar ways.

Other visualizations depend on cloning other repos. For instance, we
put [data from Italy's Civil Protection
Department](https://github.com/pcm-dpc/COVID-19) into
*resources/Italia-COVID-19* for visualizations from the `italia`
namespace.


## License

MIT License

Copyright (c) 2020 David Liepmann, Jack Rusher
