'use strict';
const $ = id => document.getElementById(id);
let token = null, timer = null, polling = false;
function limparQr() { $('qr-area').hidden = true; $('qr').removeAttribute('src'); }
function encerrar() {
  token=null; clearInterval(timer); timer=null; limparQr();
  $('acesso').hidden=false; $('conexao').hidden=true; $('estado').textContent='Aguardando acesso';
  $('estado').classList.remove('online');
}
async function api(url, options={}) {
  const response=await fetch(url, {...options, cache:'no-store', headers:{'Content-Type':'application/json',
    ...(token ? {Authorization:'Bearer '+token} : {}), ...options.headers}});
  if (!response.ok) {
    if(response.status===401) encerrar();
    const errors={401:'Login ou sessão inválidos. Entre novamente.',403:'A conexão exige uma conta administrativa.',
      409:'O gerenciamento da Evolution não está habilitado neste ambiente.',502:'A Evolution está indisponível. Tente novamente.',
      503:'A integração está desativada neste ambiente.',504:'A Evolution demorou para responder. Tente novamente.'};
    throw new Error(errors[response.status] || 'Não foi possível concluir a operação.');
  }
  return response.status===204 ? null : response.json();
}
function mostrarEstado(estado) {
  const conectado=estado==='open';
  $('estado').textContent=({open:'Conectado',close:'Desconectado',connecting:'Aguardando conexão'})[estado] || 'Estado desconhecido';
  $('estado').classList.toggle('online',conectado); $('conectar').hidden=conectado;
  $('orientacao').textContent=conectado ? 'O WhatsApp está conectado. Novas mensagens serão registradas nos chamados.'
    : 'Gere o QR Code e conecte o celular de suporte. Se o código expirar, gere outro.';
  if(conectado) limparQr();
}
async function consultar() {
  if(!token || polling)return;
  polling=true;
  try {
    const result=await api('/api/whatsapp/status');
    if(!token)return;
    $('instancia').textContent=result.instancia; mostrarEstado(result.estado); $('aviso').textContent='';
  } catch(error) {
    $('estado').textContent='Sem comunicação'; $('estado').classList.remove('online');
    $('aviso').textContent=error.message;
  } finally { polling=false; }
}
$('login-form').addEventListener('submit', async event=>{
  event.preventDefault(); const button=event.submitter; button.disabled=true; $('aviso').textContent='';
  try {
    const result=await api('/api/auth/login',{method:'POST',body:JSON.stringify({login:$('login').value,senha:$('senha').value})});
    if(result.tecnico.perfil!=='ADMIN') {
      await api('/api/auth/logout',{method:'POST',headers:{Authorization:'Bearer '+result.token}});
      throw new Error('Entre com uma conta administrativa para conectar o WhatsApp.');
    }
    token=result.token; $('acesso').hidden=true; $('conexao').hidden=false;
    await consultar(); timer=setInterval(consultar,5000);
  } catch(error) { $('aviso').textContent=error.message; }
  finally { $('senha').value=''; button.disabled=false; }
});
$('conectar').addEventListener('click', async()=>{
  $('conectar').disabled=true; $('aviso').textContent=''; limparQr();
  try {
    const result=await api('/api/whatsapp/conectar',{method:'POST'});
    if(!token)return;
    mostrarEstado(result.estado);
    if(result.qrCode) { $('qr').src=result.qrCode; $('qr-area').hidden=false; }
    else if(result.estado!=='open')$('aviso').textContent='O QR Code está sendo preparado. Clique novamente em alguns segundos.';
  } catch(error) { $('aviso').textContent=error.message; }
  finally { $('conectar').disabled=false; }
});
$('sair').addEventListener('click',async()=>{
  try { await api('/api/auth/logout',{method:'POST'}); }
  finally { encerrar(); }
});
