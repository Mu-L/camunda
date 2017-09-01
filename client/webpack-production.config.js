var path = require('path');
var webpack = require('webpack');
var CompressionPlugin = require('compression-webpack-plugin');
var HtmlWebpackIncludeAssetsPlugin = require('html-webpack-include-assets-plugin');
var config = require('./webpack.config');

config.plugins = addProductionPlugins();
config.module.rules = correctLoadersForProduction(config.module.rules);

// ATTENTION: Might need to be adjusted
// Root of static resources
// So every static resource will be have absolute path (from web server server root ofc) if that is set with this value
// as prefix or very path will be relative otherwise (relative paths will break loading of resources in spa apps).
config.output.publicPath = '/';

module.exports = config;

function addProductionPlugins() {
  return config.plugins.concat([
    new HtmlWebpackIncludeAssetsPlugin({
      assets: ['styles.css'],
      append: false
    }),
    new webpack.DefinePlugin({
      'process.env.NODE_ENV': JSON.stringify('production')
    }),
    new webpack.optimize.UglifyJsPlugin({
      compress: {
        warnings: false
      }
    }),
    new CompressionPlugin({
      asset: '[path].gz[query]',
      algorithm: 'gzip',
      test: /\.js$|\.css|\.eot|\.ttf|\.svg$/
    })
  ]);
}

function correctLoadersForProduction(rules) {
  return rules.map(function(loader) {
    if (loader.loader === 'url-loader' && !loader.test.test('.gif')) {
      return Object.assign(loader, {
        loader: 'file-loader'
      });
    } else if (Array.isArray(loader.loaders) && loader.loaders.indexOf('style-loader') !== -1) {
      return Object.assign(loader, {
        loaders: [
          'style-loader/url',
          'file-loader?name=[name].css',
          'extract-loader',
          'css-loader',
          'less-loader'
        ]
      });
    }

    return loader;
  });
}
