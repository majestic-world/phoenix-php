# Phoenix PHP

> Melhor suporte para helpers PHP no **PhpStorm 2026.2+**.

O Phoenix PHP adiciona autocomplete e navegação para helpers simples que normalmente não podem ser entendidos pelo IDE.

| Helper | O que o plugin entrega |
| --- | --- |
| `projectDir()` | Arquivos e pastas relativos à anotação ou a `dirname(__DIR__, N)` |
| `view()` | Templates do Plates sem a extensão do arquivo |
| `assets()` | Arquivos e pastas da base configurada por `@phoenix-asset` |
| `env()` | Chaves do arquivo `.env`, preservando o casing original |

No autocomplete de `projectDir()`, `view()` e helpers anotados com `@phoenix-asset`, a busca não diferencia maiúsculas de minúsculas: `comma`, `COMMA` e `cOmMa` encontram `Command`. A seleção insere o nome real, preservando a caixa original, a extensão dos arquivos em `projectDir()` e assets e a `/` final das pastas; os templates de `view()` continuam sem extensão. Isso não normaliza nem altera a resolução dos diretórios já digitados no caminho.

## projectDir

```php
/**
 * @phoenix-project-dir src
 */
function projectDir(?string $path = null): string
{
    $root = dirname(__DIR__, 2) . '/src';
    return $root . ($path === null ? '' : '/' . ltrim($path, '/\\'));
}

projectDir('Model/Mail.php'); // src/Model/Mail.php
```

`@phoenix-project-dir src` define `<raiz do projeto>/src` como base da IDE, independentemente da localização do helper. O valor da anotação é obrigatório e pode ser outra pasta relativa, como `resources` ou `src\Model`. Em projetos com múltiplas raízes de conteúdo, usa a raiz do arquivo que faz a chamada.

Use <kbd>Ctrl</kbd> + <kbd>Space</kbd> para listar arquivos e pastas a partir dessa base. Os caminhos aceitam `/` e `\`, com ou sem barra inicial: `Model/Mail.php` e `/Model/Mail.php` apontam para o mesmo arquivo dentro de `src`. <kbd>Ctrl</kbd> + clique ou <kbd>Ctrl</kbd> + <kbd>B</kbd> abre arquivos no editor e revela pastas na árvore do projeto.

A anotação pode ser aplicada a funções e métodos de qualquer nome e reconhece o primeiro parâmetro declarado, inclusive em chamadas com argumentos nomeados. Ela tem prioridade sobre a detecção de `dirname(__DIR__, N)`; sem anotação, a detecção antiga de `projectDir()` continua disponível. Valores vazios, pastas inexistentes, caminhos absolutos ou com `..` não fornecem sugestões.

Isso só informa a base para a IDE: não altera o retorno nem a execução da função PHP. A implementação do helper deve continuar retornando o caminho real desejado.

## view

```php
use League\Plates\Engine;

/**
 * @phoenix-view resources/views
 */
function view(string $name, array $data = []): void
{
    static $engine = null;

    $engine ??= new Engine(projectDir('resources/views'));

    echo $engine->render($name, $data);
}

view('admin/home'); // resources/views/admin/home.php
```

O autocomplete mostra nomes de templates sem extensão e a navegação abre o arquivo correspondente.

Use `@phoenix-view` no PHPDoc da função ou do método que recebe o nome do template como primeiro parâmetro. O nome da função ou do método é livre; funciona com métodos de instância, métodos estáticos e funções importadas com alias.

A pasta é relativa à raiz do projeto, não ao arquivo do helper. Em projetos com múltiplas raízes de conteúdo, usa a raiz do arquivo que faz a chamada. Aceita `resources/views` e `resources\views`; não use caminhos absolutos ou `..`.

O autocomplete também funciona em subpastas e com argumentos nomeados. <kbd>Ctrl</kbd> + clique ou <kbd>Ctrl</kbd> + <kbd>B</kbd> no nome abre o template.

A anotação tem prioridade sobre a detecção do corpo da função. Sem anotação, a detecção de `new Engine(__DIR__ . '...')` em `view()` continua disponível. Uma pasta anotada inexistente não gera sugestões de outro diretório. A anotação é usada apenas pela IDE e não altera a execução do PHP.

## assets

```php
/**
 * @phoenix-asset public/assets
 */
function assets(string $n, bool $id = true): string
{
    static $version = null;
    if (!$version) {
        $version = crc32(APP_VERSION);
    }

    $assetUrl = projectUrl('assets/' . $n);
    return $assetUrl . ($id ? "?id=$version" : '');
}

assets('js/app.js'); // public/assets/js/app.js
```

`@phoenix-asset public/assets` define a pasta relativa à raiz usada pelo autocomplete. O valor é obrigatório; aceita `/` ou `\`, mas não caminhos absolutos nem `..`. Arquivos mantêm a extensão e pastas recebem `/` ao serem selecionadas.

A anotação funciona em funções e métodos de qualquer nome, inclusive funções importadas com alias e chamadas com argumentos nomeados. Somente o primeiro parâmetro declarado recebe sugestões. <kbd>Ctrl</kbd> + clique ou <kbd>Ctrl</kbd> + <kbd>B</kbd> abre o arquivo ou a pasta correspondente.

Isso só fornece contexto para a IDE. Não altera a função, a URL retornada nem qualquer comportamento em runtime.

## env

```php
function env(string $key, mixed $default = null): mixed
{
    return $_ENV[$key] ?? $default;
}

env('ADMIN_ROUTER');
```

As chaves são lidas do `.env`. A sugestão é case-insensitive, mas a inserção sempre mantém exatamente o nome definido no arquivo — por exemplo, `ADMIN_ROUTER`.

## Struct do model

Associe um model de `Rider\System\Database\Model` à classe gerada com `@phoenix-struct`:

```php
namespace Seek\Horus\Model;

use Rider\System\Database\Model;

/**
 * @phoenix-struct \Seek\Horus\Struct\MailData
 */
class Mail extends Model
{
    protected string $table = 'mail';
}
```

O PhpStorm passa a usar as propriedades e os tipos de `MailData` nos resultados das consultas:

```php
$model = new Mail();
$mail = $model->random();
$mail?->host;

foreach ($model->findAll() as $mail) {
    echo $mail->email;
}

$model->orderBy('id')->findById(1)?->port;
```

A anotação fornece contexto para `findById()`, `first()`, `random()`, `findAll()`, `whereIn()`, `whereNotIn()` e `search()`. Em `findBy()` e `between()`, o argumento `all` distingue registro único e coleção; quando seu valor não é conhecido estaticamente, ambas as possibilidades são mantidas.

`orderBy()` continua retornando o model. Métodos sobrescritos, modelos sem anotação, agregações e SQL livre (`sum()` e `query()`) mantêm a inferência nativa.

A inferência é complementar: o PhpStorm pode continuar exibindo `object`, `array` ou `mixed` junto ao contexto específico da struct. A anotação não substitui os tipos nativos do método.

Isso existe apenas na IDE: não instancia `MailData`, não hidrata registros e não altera retornos ou execução do ORM. O objeto real continua sendo o retornado pelo banco; a anotação não garante `instanceof MailData`.

### Resultados armazenados em propriedades

Ao armazenar o resultado em uma propriedade declarada como `?object`, informe o tipo mais específico para a IDE com o PHPDoc padrão `@var`, usando a mesma classe indicada no model:

```php
/** @var \Seek\Horus\Struct\MailData|null */
private static ?object $server = null;
```

`@phoenix-struct` fornece contexto ao retorno das consultas; não reescreve a declaração de uma propriedade. O `@var` mantém as sugestões em acessos como `self::$server->host` e em outros métodos da classe, sem depender de rastrear a atribuição feita pelo ORM.

Mantenha o tipo nativo `?object` se o ORM retorna objetos genéricos. Trocar esse tipo por `?MailData` alteraria a validação em runtime e poderia causar `TypeError`, pois a struct não é instanciada pelo plugin. O PHPDoc é suficiente para a IDE e não exige outra anotação específica do Phoenix.

## PHPDoc @throws compacto

No <kbd>Alt</kbd> + <kbd>Enter</kbd> de uma exceção não documentada, escolha **Add compact PHPDoc @throws tags** para gerar:

```php
/** @throws Exception */
```

A ação usa a análise de exceções e a geração de imports do PhpStorm. Está disponível para funções e métodos que ainda não têm PHPDoc; a ação nativa **Add PHPDoc comment with @throws tags** continua disponível e não é substituída.

Um único `@throws` sem descrição fica em uma linha, inclusive após **Reformat Code** (<kbd>Ctrl</kbd> + <kbd>Alt</kbd> + <kbd>L</kbd>). A reformatação também compacta comentários existentes com esse formato simples. Comentários com descrições, outras tags ou vários `@throws` permanecem em múltiplas linhas.

O delimitador é `/**`, com dois asteriscos: `/***` não é PHPDoc para o PHP. A alteração é documental e não muda a execução das funções.

---

Desenvolvido para **PhpStorm 2026.2+** · Requer Java 25 para desenvolver e executar o plugin.
