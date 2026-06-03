function fn() {
  var env = karate.env || 'dev';
  var config = {
    libertyBaseUrl: karate.properties['liberty.base.url'] || 'http://localhost:9080/webui-1.0/banking',
    zosConnectBaseUrl: karate.properties['zosconnect.base.url'] || 'http://localhost:8080',
    sortCode: '987654'
  };
  karate.configure('connectTimeout', 30000);
  karate.configure('readTimeout', 30000);
  return config;
}
