'use strict';
/* This worker parses source as data. It never executes user JavaScript. */
importScripts('./vendor/acorn.js', './catalogue.js', './core.js', './authoring.js', './easy.js');
self.onmessage = function (event) {
  const request = event.data || {};
  try {
    const engine = self.EditRinBlocks;
    if (!engine) throw new Error('Source engine is unavailable');
    let result;
    if (request.type === 'parse') result = engine.parse(request.source, request.options || {});
    else if (request.type === 'proposePatch') result = engine.proposePatch(request.projection, request.operations);
    else if (request.type === 'authoringInspect') result = self.EditRinBlockAuthoring.inspect(request.projection, request.env || {});
    else if (request.type === 'authoringPlan') result = self.EditRinBlockAuthoring.plan(request.projection, request.request, request.env || {});
    else if (request.type === 'renderNode') result = engine.renderNode(request.projection, request.nodeId, request.overrides || {});
    else if (request.type === 'easyCapsuleEdit') {
      const graph = self.EditRinEasyBlocks.buildEasyGraph(request.projection, {language: request.language || 'ja'});
      const item = graph.items.get(request.itemId);
      result = self.EditRinEasyBlocks.proposeCapsuleEdit(request.projection, item, request.capsuleKey, request.value);
    } else if (request.type === 'easyTrailColorEdit') {
      const graph = self.EditRinEasyBlocks.buildEasyGraph(request.projection, {language: request.language || 'ja'});
      const item = graph.items.get(request.itemId);
      result = self.EditRinEasyBlocks.proposeTrailColorEdit(request.projection, item, request.hex);
    } else if (request.type === 'easyValidateExpr') {
      result = self.EditRinEasyBlocks.validateExpression(request.expression, request.sourceType || 'script');
    } else throw new Error('Unknown source operation');
    self.postMessage({id:request.id,ok:true,result});
  } catch (error) {
    self.postMessage({id:request.id,ok:false,error:String(error && error.message || error)});
  }
};
