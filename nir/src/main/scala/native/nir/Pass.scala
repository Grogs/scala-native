package native
package nir

import scala.collection.mutable

trait Pass extends (Seq[Defn] => Seq[Defn]) {
  type OnAssembly = PartialFunction[Seq[Defn], Seq[Defn]]
  type OnDefn = PartialFunction[Defn, Seq[Defn]]
  type OnBlock = PartialFunction[Block, Seq[Block]]
  type OnInst = PartialFunction[Inst, Seq[Inst]]
  type OnCf = PartialFunction[Cf, Cf]
  type OnNext = PartialFunction[Next, Next]
  type OnVal = PartialFunction[Val, Val]
  type OnType = PartialFunction[Type, Type]

  def preAssembly: OnAssembly = null
  def postAssembly: OnAssembly = null
  def preDefn: OnDefn = null
  def postDefn: OnDefn = null
  def preBlock: OnBlock = null
  def postBlock: OnBlock = null
  def preInst: OnInst = null
  def postInst: OnInst = null
  def preCf: OnCf = null
  def postCf: OnCf = null
  def preNext: OnNext = null
  def postNext: OnNext = null
  def preVal: OnVal = null
  def postVal: OnVal = null
  def preType: OnType = null
  def postType: OnType = null

  @inline private def hook[A, B](pf: PartialFunction[A, B], arg: A, default: B): B =
    if (pf == null) default else pf.applyOrElse(arg, (_: A) => default)

  private def txAssembly(assembly: Seq[Defn]): Seq[Defn] = {
    val pre = hook(preAssembly, assembly, assembly)

    val post =
      pre.flatMap { defn =>
        val pres = hook(preDefn, defn, Seq(defn))
        val posts = pres.flatMap(txDefn)

        posts.flatMap(post => hook(postDefn, post, Seq(post)))
      }

    hook(postAssembly, post, post)
  }

  private def txDefn(defn: Defn): Seq[Defn] = {
    val pres = hook(preDefn, defn, Seq(defn))

    pres.flatMap { pre =>
      val post = pre match {
        case defn @ Defn.Var(_, _, ty, value) =>
          defn.copy(ty = txType(ty), value = txVal(value))
        case defn @ Defn.Const(_, _, ty, value) =>
          defn.copy(ty = txType(ty), value = txVal(value))
        case defn @ Defn.Declare(_, _, ty) =>
          defn.copy(ty = txType(ty))
        case defn @ Defn.Define(_, _, ty, blocks) =>
          defn.copy(ty = txType(ty), blocks = blocks.flatMap(txBlock))
        case defn @ Defn.Struct(_, _, fieldtys) =>
          defn.copy(fieldtys = fieldtys.map(txType))
        case defn @ Defn.Interface(_, _, _, members) =>
          defn.copy(members = members.flatMap(txDefn))
        case defn @ Defn.Class(_, _, _, _, members) =>
          defn.copy(members = members.flatMap(txDefn))
        case defn @ Defn.Module(_, _, _, _, members) =>
          defn.copy(members = members.flatMap(txDefn))
      }

      hook(postDefn, post, Seq(post))
    }
  }

  private def txBlock(block: Block): Seq[Block] = {
    val pres = hook(preBlock, block, Seq(block))

    pres.flatMap { pre =>
      val newparams = pre.params.map { param =>
        Val.Local(param.name, txType(param.ty))
      }
      val newinsts = pre.insts.flatMap(txInst)
      val newcf = txCf(pre.cf)
      val post = Block(pre.name, newparams, newinsts, newcf)

      hook(postBlock, post, Seq(post))
    }
  }

  private def txInst(inst: Inst): Seq[Inst] = {
    val pres = hook(preInst, inst, Seq(inst))

    pres.flatMap { pre =>
      val newop = pre.op match {
        case Op.Call(ty, ptrv, argvs)        => Op.Call(txType(ty), txVal(ptrv), argvs.map(txVal))
        case Op.Load(ty, ptrv)               => Op.Load(txType(ty), txVal(ptrv))
        case Op.Store(ty, ptrv, v)           => Op.Store(txType(ty), txVal(ptrv), txVal(v))
        case Op.Elem(ty, ptrv, indexvs)      => Op.Elem(txType(ty), txVal(ptrv), indexvs.map(txVal))
        case Op.Extract(ty, aggrv, indexv)   => Op.Extract(txType(ty), txVal(aggrv), txVal(indexv))
        case Op.Insert(ty, aggrv, v, indexv) => Op.Insert(txType(ty), txVal(aggrv), txVal(v), txVal(indexv))
        case Op.Alloca(ty)                   => Op.Alloca(txType(ty))
        case Op.Bin(bin, ty, lv, rv)         => Op.Bin(bin, txType(ty), txVal(lv), txVal(rv))
        case Op.Comp(comp, ty, lv, rv)       => Op.Comp(comp, txType(ty), txVal(lv), txVal(rv))
        case Op.Conv(conv, ty, v)            => Op.Conv(conv, txType(ty), txVal(v))

        case Op.Alloc(ty)        => Op.Alloc(txType(ty))
        case Op.Field(ty, v, n)  => Op.Field(txType(ty), txVal(v), n)
        case Op.Method(ty, v, n) => Op.Method(txType(ty), txVal(v), n)
        case Op.Module(n)        => Op.Module(n)
        case Op.As(ty, v)        => Op.As(txType(ty), txVal(v))
        case Op.Is(ty, v)        => Op.Is(txType(ty), txVal(v))
        case Op.Copy(v)          => Op.Copy(txVal(v))
        case Op.SizeOf(ty)       => Op.SizeOf(txType(ty))
        case Op.TypeOf(ty)       => Op.TypeOf(txType(ty))
        case Op.StringOf(_)      => pre.op
      }
      val post = Inst(pre.name, newop)

      hook(postInst, post, Seq(post))
    }
  }

  private def txCf(cf: Cf): Cf = {
    val pre = hook(preCf, cf, cf)
    val post = pre match {
      case Cf.Unreachable                         => Cf.Unreachable
      case Cf.Ret(v)                              => Cf.Ret(txVal(v))
      case Cf.Jump(next)                          => Cf.Jump(txNext(next))
      case Cf.If(v, thenp, elsep)                 => Cf.If(txVal(v), txNext(thenp), txNext(elsep))
      case Cf.Switch(v, default, cases)           => Cf.Switch(txVal(v), txNext(default), cases.map(txCase))
      case Cf.Invoke(ty, ptrv, argvs, succ, fail) => Cf.Invoke(txType(ty), txVal(ptrv), argvs.map(txVal), txNext(succ), txNext(fail))

      case Cf.Throw(v)       => Cf.Throw(txVal(v))
      case Cf.Try(norm, exc) => Cf.Try(txNext(norm), txNext(exc))
    }

    hook(postCf, post, post)
  }

  private def txVal(value: Val): Val = {
    val pre = hook(preVal, value, value)
    val post = pre match {
      case Val.Zero(ty)          => Val.Zero(txType(ty))
      case Val.Struct(n, values) => Val.Struct(n, values.map(txVal))
      case Val.Array(ty, values) => Val.Array(txType(ty), values.map(txVal))
      case Val.Local(n, ty)      => Val.Local(n, txType(ty))
      case Val.Global(n, ty)     => Val.Global(n, txType(ty))
      case _                     => pre
    }

    hook(postVal, post, post)
  }

  private def txType(ty: Type): Type = {
    val pre = hook(preType, ty, ty)
    val post = pre match {
      case Type.Array(ty, n)      => Type.Array(txType(ty), n)
      case Type.Ptr(ty)           => Type.Ptr(txType(ty))
      case Type.Function(tys, ty) => Type.Function(tys.map(txType), txType(ty))
      case _                      => pre
    }

    hook(postType, post, post)
  }

  private def txNext(next: Next) = {
    val pre = hook(preNext, next, next)
    val post = Next(pre.name, pre.args.map(txVal))

    hook(postNext, post, post)
  }

  private def txCase(kase: Case) =
    Case(txVal(kase.value), txNext(kase.next))

  final def apply(assembly: Seq[Defn]): Seq[Defn] =
    txAssembly(assembly)
}
