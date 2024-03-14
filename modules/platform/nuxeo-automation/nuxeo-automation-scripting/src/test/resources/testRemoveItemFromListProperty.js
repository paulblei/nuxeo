function run(input, params) {
  Document.RemoveItemFromListProperty(
    input, {
        'xpath': 'df:documentIds',
        'index': (input['df:documentIds'].length - 1)
    }
  );
}
