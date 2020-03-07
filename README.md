# COVID19 Visualizations in Clojure with Vega

I wanted to better understand COVID-19, so I cloned Johns Hopkins'
daily-updated dataset, fired up a Clojure REPL, and started massaging
the data into a visualization using the Vega grammar. This is a
cleaned-up subset of the code I used.


## Usage

1. Clone the [Johns Hopkins dataset repo](https://github.com/CSSEGISandData/COVID-19) to *resources/* within this repo
2. Open `appliedsciencestudio.covid19-clj-viz.viz`, connect a Clojure
CLI REPL, and evaluate forms. Oz (the Vega wrapper) will open a
browser window to display the visualizations.


## License

MIT License

Copyright (c) 2020 David Liepmann, Jack Rusher
