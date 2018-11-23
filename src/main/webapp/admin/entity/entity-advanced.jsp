<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<%@ include file="/_include/Head.jsp"%>
<title>高级配置</title>
<style type="text/css">
a#entityIcon{display:inline-block;width:36px;height:36px;background-color:#e3e3e3;text-align:center;border-radius:2px;}
a#entityIcon .icon{font-size:26px;color:#555;line-height:36px;}
a#entityIcon:hover{opacity:0.8}
</style>
</head>
<body>
<div class="rb-wrapper rb-fixed-sidebar rb-collapsible-sidebar rb-collapsible-sidebar-hide-logo rb-aside rb-color-header">
	<jsp:include page="/_include/NavTop.jsp">
		<jsp:param value="实体管理" name="pageTitle"/>
	</jsp:include>
	<jsp:include page="/_include/NavLeftAdmin.jsp">
		<jsp:param value="entities" name="activeNav"/>
	</jsp:include>
	<div class="rb-content">
		<aside class="page-aside">
			<div class="rb-scroller-aside rb-scroller">
				<div class="aside-content">
					<div class="content">
						<div class="aside-header">
							<button class="navbar-toggle collapsed" type="button"><span class="icon zmdi zmdi-caret-down"></span></button>
							<span class="title">${entityLabel}</span>
							<p class="description">${comments}</p>
						</div>
					</div>
					<div class="aside-nav collapse">
						<ul class="nav">
							<li><a href="base">基本信息</a></li>
							<li><a href="fields">管理字段</a></li>
							<li><a href="form-design">设计布局</a></li>
							<li class="active"><a href="advanced">高级配置</a></li>
						</ul>
					</div>
				</div>
			</div>
        </aside>
		<div class="page-head">
			<div class="page-head-title">高级配置</div>
		</div>
		<div class="main-content container-fluid pt-1">
			<div class="card">
				<div class="card-header">删除实体</div>
				<div class="card-body">
					<p><strong>实体删除后将无法恢复，请务必谨慎操作。</strong>删除前，必须将该实体下的记录全部清空。如果这是一个主实体，则需要先将明细实体删除。</p>
					<div>
						<label class="custom-control custom-control-sm custom-checkbox custom-control-inline mb-2">
							<input class="custom-control-input J_drop-check" type="checkbox"><span class="custom-control-label"> 我已知晓风险</span>
						</label>
					</div>
					<div>
						<button type="button" class="btn btn-danger J_drop-confirm" disabled="disabled" data-loading-text="删除中"><i class="zmdi zmdi-delete icon"></i> 确认删除</button>
						<div class="alert alert-warning alert-icon hide col-sm-6">
							<div class="icon"><span class="zmdi zmdi-alert-triangle"></span></div>
							<div class="message">系统内建实体，不允许删除</div>
						</div>
					</div>
				</div>
			</div>
		</div>
	</div>
</div>
<%@ include file="/_include/Foot.jsp"%>
<script type="text/babel">
$(document).ready(function(){
	const metaId = '${entityMetaId}'
	if (!!!metaId){
		$('.J_drop-confirm').next().removeClass('hide')
		$('.J_drop-confirm').remove()
		$('.J_drop-check').parent().parent().remove()
		return
	}
	
	$('.J_drop-check').click(function(){
		$('.J_drop-confirm').attr('disabled', $(this).prop('checked') == false)
	})
	
	const sbtn = $('.J_drop-confirm').click(function(){
		sbtn.button('loading')
		$.post('../entity-drop?id=' + metaId, function(res){
			if (res.error_code == 0){
				rb.notice('实体已删除', 'success')
				setTimeout(function(){ location.replace('../../entities') }, 1500)
			}else rb.notice(res.error_msg, 'danger')
		})
	})
})
</script>
</body>
</html>