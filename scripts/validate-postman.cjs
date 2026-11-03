const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const collection = JSON.parse(fs.readFileSync(path.join(root, 'docs/postman/triagem-chamados.postman_collection.json'), 'utf8'));
JSON.parse(fs.readFileSync(path.join(root, 'docs/postman/local.postman_environment.json'), 'utf8'));
JSON.parse(fs.readFileSync(path.join(root, 'postman/contracts/evolution-v2.json'), 'utf8'));
let requests=0, scripts=0;
function visit(node) {
  for (const event of node.event || []) {
    new vm.Script(event.script.exec.join('\n'), { filename: node.name + ':' + event.listen });
    scripts++;
  }
  if (node.request) {
    requests++;
    if (node.request.body?.mode === 'raw' && node.request.body.options?.raw?.language === 'json')
      JSON.parse(node.request.body.raw.replace(/\{\{[^{}]+\}\}/g, '1'));
  }
  for (const child of node.item || []) visit(child);
}
visit(collection);
console.log(`JSONs válidos; ${requests} requisições e ${scripts} scripts com sintaxe válida.`);
