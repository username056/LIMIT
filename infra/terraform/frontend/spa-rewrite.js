function handler(event) {
  var request = event.request;
  var uri = request.uri;
  var lastSegment = uri.substring(uri.lastIndexOf('/') + 1);

  if (uri.startsWith('/releases/')) {
    return {
      statusCode: 404,
      statusDescription: 'Not Found',
      headers: {
        'cache-control': { value: 'no-store' },
      },
    };
  }

  if (uri.endsWith('/')) {
    request.uri += 'index.html';
  } else if (!lastSegment.includes('.')) {
    request.uri = '/index.html';
  }

  return request;
}
